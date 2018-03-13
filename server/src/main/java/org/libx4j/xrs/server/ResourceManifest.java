/* Copyright (c) 2016 lib4j
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.libx4j.xrs.server;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Providers;

import org.lib4j.util.JavaIdentifiers;
import org.libx4j.xrs.server.core.AnnotationInjector;
import org.libx4j.xrs.server.util.MediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceManifest {
  private static final Logger logger = LoggerFactory.getLogger(ResourceManifest.class);

  private static boolean logMissingHeaderWarning(final String headerName, final Class<?> type) {
    logger.warn("Unmatched @" + type.getSimpleName() + " for " + headerName);
    return false;
  }

  private static Annotation findSecurityAnnotation(final Method method) {
    final Annotation annotation = findSecurityAnnotation(method.getAnnotations());
    return annotation != null ? annotation : findSecurityAnnotation(method.getDeclaringClass().getAnnotations());
  }

  private static Annotation findSecurityAnnotation(final Annotation ... annotations) {
    for (final Annotation annotation : annotations)
      if (annotation.annotationType() == PermitAll.class || annotation.annotationType() == DenyAll.class || annotation.annotationType() == RolesAllowed.class)
        return annotation;

    return null;
  }

  private final HttpMethod httpMethod;
  private final Annotation securityAnnotation;
  private final Method method;
  private final Object singleton;
  private final Class<?> serviceClass;
  private final PathPattern pathPattern;
  private final MediaTypeMatcher<Consumes> consumesMatcher;
  private final MediaTypeMatcher<Produces> producesMatcher;

  public ResourceManifest(final HttpMethod httpMethod, final Method method, final Object singleton) {
    this.httpMethod = httpMethod;
    final Annotation securityAnnotation = findSecurityAnnotation(method);
    this.securityAnnotation = securityAnnotation != null ? securityAnnotation : new PermitAll() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return getClass();
      }
    };
    this.method = method;
    this.singleton = singleton;
    this.serviceClass = singleton != null ? singleton.getClass() : method.getDeclaringClass();
    this.pathPattern = new PathPattern(method);
    this.consumesMatcher = new MediaTypeMatcher<Consumes>(method, Consumes.class);
    this.producesMatcher = new MediaTypeMatcher<Produces>(method, Produces.class);
  }

  public Object getSingleton() {
    return this.singleton;
  }

  public Class<?> getServiceClass() {
    return this.serviceClass;
  }

  public MediaType matches(final ContainerRequestContext containerRequestContext) {
    if (!httpMethod.value().toUpperCase().equals(containerRequestContext.getMethod()))
      return null;

    final String path = containerRequestContext.getUriInfo().getPath();
    if (!pathPattern.matches(path))
      return null;

    final MediaType[] accept = MediaTypes.parse(containerRequestContext.getHeaders().get(HttpHeaders.ACCEPT));
    final MediaType acceptedType = producesMatcher.matches(accept);
    if (acceptedType == null)
      return null;

    final MediaType[] contentType = MediaTypes.parse(containerRequestContext.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    if (consumesMatcher.matches(contentType) == null)
      return null;

    return acceptedType;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object[] getParameters(final Method method, final ContainerRequestContext containerRequestContext, final AnnotationInjector annotationInjector, final List<ProviderResource<ParamConverterProvider>> paramConverterProviders) throws IOException {
    final Parameter[] parameters = method.getParameters();
    final Type[] genericParameterTypes = method.getGenericParameterTypes();
    final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    if (parameters.length == 0)
      return null;

    final Object[] parameterInstances = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      final Parameter parameter = parameters[i];
      final Type genericParameterType = genericParameterTypes[i];
      final Annotation[] annotations = parameterAnnotations[i];
      final Annotation paramAnnotation = AnnotationInjector.getInjectableAnnotation(parameter, annotations);
      if (paramAnnotation == null) {
        final Providers providers = annotationInjector.getContextObject(Providers.class);
        final MessageBodyReader messageBodyReader = providers.getMessageBodyReader(parameter.getType(), genericParameterType, annotations, containerRequestContext.getMediaType());
        if (messageBodyReader != null)
          parameterInstances[i] = messageBodyReader.readFrom(parameter.getType(), parameter.getType().getGenericSuperclass(), parameter.getAnnotations(), containerRequestContext.getMediaType(), containerRequestContext.getHeaders(), containerRequestContext.getEntityStream());
        else
          throw new WebApplicationException("Could not find MessageBodyReader for type: " + parameter.getName());
      }
      else {
        try {
          parameterInstances[i] = annotationInjector.getParamObject(paramAnnotation, parameter.getType(), annotations, genericParameterType, paramConverterProviders);
        }
        catch (final ReflectiveOperationException e) {
          if (paramAnnotation.annotationType() == MatrixParam.class || paramAnnotation.annotationType() == QueryParam.class || paramAnnotation.annotationType() == PathParam.class)
            throw new NotFoundException(e);

          throw new BadRequestException(e);
        }
      }
    }

    return parameterInstances;
  }

  protected boolean checkHeader(final String headerName, final Class<? extends Annotation> annotationClass, final ContainerRequestContext containerRequestContext) {
    final Annotation annotation = getMatcher(annotationClass).getAnnotation();
    if (annotation == null) {
      final String message = "@" + annotationClass.getSimpleName() + " annotation missing for " + method.getDeclaringClass().getName() + "." + JavaIdentifiers.toClassCase(containerRequestContext.getMethod().toLowerCase()) + "()";
      if (annotationClass == Consumes.class)
        throw new RuntimeException(message);

      logger.warn(message);
      return true;
    }

    final String headerValue = containerRequestContext.getHeaderString(headerName);
    if (headerValue == null || headerValue.length() == 0)
      return logMissingHeaderWarning(headerName, annotationClass);

    final String[] headerValueParts = headerValue.split(",");
    final MediaType[] test = MediaTypes.parse(headerValueParts);

    final String[] annotationValue = annotationClass == Produces.class ? ((Produces)annotation).value() : annotationClass == Consumes.class ? ((Consumes)annotation).value() : null;
    final MediaType[] required = MediaTypes.parse(annotationValue);
    if (MediaTypes.matches(required, test) != null)
      return true;

    return logMissingHeaderWarning(headerName, annotationClass);
  }

  private static void allow(final Annotation securityAnnotation, final ContainerRequestContext containerRequestContext) {
    if (securityAnnotation instanceof PermitAll)
      return;

    if (securityAnnotation instanceof DenyAll)
      throw new ForbiddenException("@DenyAll");

    if (!(securityAnnotation instanceof RolesAllowed))
      throw new UnsupportedOperationException("Unsupported security annotation: " + securityAnnotation.getClass().getName());

    if (containerRequestContext.getSecurityContext().getUserPrincipal() != null)
      for (final String role : ((RolesAllowed)securityAnnotation).value())
        if (containerRequestContext.getSecurityContext().isUserInRole(role))
          return;

    final RolesAllowed rolesAllowed = (RolesAllowed)securityAnnotation;
    if (rolesAllowed.value().length == 1)
      throw new NotAuthorizedException(containerRequestContext.getSecurityContext().getAuthenticationScheme() != null ? containerRequestContext.getSecurityContext().getAuthenticationScheme() + " realm=\"" + rolesAllowed.value()[0] + "\"" : "realm=\"" + rolesAllowed.value()[0] + "\"");

    final String[] challenges = new String[rolesAllowed.value().length];
    if (containerRequestContext.getSecurityContext().getAuthenticationScheme() != null) {
      final String scheme = containerRequestContext.getSecurityContext().getAuthenticationScheme();
      for (int i = 0; i < challenges.length; i++)
        challenges[i] = scheme + " realm=\"" + rolesAllowed.value()[i] + "\"";
    }
    else {
      for (int i = 0; i < challenges.length; i++)
        challenges[i] = "realm=\"" + rolesAllowed.value()[i] + "\"";
    }

    throw new NotAuthorizedException(challenges);
  }

  public Object service(final ExecutionContext executionContext, final ContainerRequestContext containerRequestContext, final AnnotationInjector injectionContext, final List<ProviderResource<ParamConverterProvider>> paramConverterProviders) throws IOException, ServletException {
    if (executionContext.getMatchedResources() == null)
      throw new IllegalStateException("service() called before filterAndMatch()");

    if (executionContext.getMatchedResources().size() == 0)
      throw new IllegalStateException("should have already issued 404");

    // FIXME: The ExecutionContext instance contains the resource object already instantiated for
    // FIXME: the serviceClass in this ResourceManifest. The cohesion needs to be made tighter!
    final Object serviceResource = executionContext.getMatchedResources().get(0);
    allow(securityAnnotation, containerRequestContext);

    try {
      final Object[] parameters = getParameters(method, containerRequestContext, injectionContext, paramConverterProviders);
      return parameters != null ? method.invoke(serviceResource, parameters) : method.invoke(serviceResource);
    }
    catch (final IllegalAccessException e) {
      throw new ServletException(e);
    }
    catch (final InvocationTargetException e) {
      // FIXME: Hmm, this is an interesting idea to help reduce the noise in Exceptions from dynamically invoked methods
      if (e.getCause() instanceof RuntimeException)
        throw (RuntimeException)e.getCause();

      if (e.getCause() instanceof ServletException)
        throw (ServletException)e.getCause();

      if (e.getCause() instanceof IOException)
        throw (IOException)e.getCause();

      throw new ServletException(e.getCause());
    }
    catch (final IllegalArgumentException e) {
      throw new BadRequestException(e);
    }
  }

  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  public PathPattern getPathPattern() {
    return pathPattern;
  }

  @SuppressWarnings("unchecked")
  public <T extends Annotation>MediaTypeMatcher<T> getMatcher(final Class<T> annotationClass) {
    return annotationClass == Consumes.class ? (MediaTypeMatcher<T>)consumesMatcher : annotationClass == Produces.class ? (MediaTypeMatcher<T>)producesMatcher : null;
  }

  public boolean isRestricted() {
    return securityAnnotation instanceof DenyAll || securityAnnotation instanceof RolesAllowed;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this)
      return true;

    if (!(obj instanceof ResourceManifest))
      return false;

    final ResourceManifest that = (ResourceManifest)obj;
    return httpMethod.equals(that.httpMethod) && securityAnnotation.equals(that.securityAnnotation) && method.equals(that.method) && serviceClass.equals(that.serviceClass) && pathPattern.equals(that.pathPattern) && consumesMatcher.equals(that.consumesMatcher) && producesMatcher.equals(that.producesMatcher);
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    hashCode *= 31 ^ hashCode + httpMethod.hashCode();
    hashCode *= 31 ^ hashCode + securityAnnotation.hashCode();
    hashCode *= 31 ^ hashCode + method.hashCode();
    hashCode *= 31 ^ hashCode + serviceClass.hashCode();
    hashCode *= 31 ^ hashCode + pathPattern.hashCode();
    hashCode *= 31 ^ hashCode + consumesMatcher.hashCode();
    hashCode *= 31 ^ hashCode + producesMatcher.hashCode();
    return hashCode;
  }

  @Override
  public String toString() {
    return serviceClass.getName() + '#' + method.getName();
  }
}