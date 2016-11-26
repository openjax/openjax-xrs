/* Copyright (c) 2016 Seva Safris
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

package org.safris.xrs.server;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;

import org.safris.commons.lang.Strings;
import org.safris.xrs.server.core.ContextInjector;
import org.safris.xrs.server.util.MediaTypes;

public class ResourceManifest {
  private static final Logger logger = Logger.getLogger(ResourceManifest.class.getName());

  private static boolean logMissingHeaderWarning(final String headerName, final Class<?> type) {
    logger.warning("Unmatched @" + type.getSimpleName() + " for " + headerName);
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
  private final Class<?> serviceClass;
  private final PathPattern pathPattern;
  private final MediaTypeMatcher<Consumes> consumesMatcher;
  private final MediaTypeMatcher<Produces> producesMatcher;

  public ResourceManifest(final HttpMethod httpMethod, final Method method) {
    this.httpMethod = httpMethod;
    final Annotation securityAnnotation = findSecurityAnnotation(method);
    this.securityAnnotation = securityAnnotation != null ? securityAnnotation : new PermitAll() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return getClass();
      }
    };
    this.method = method;
    this.serviceClass = method.getDeclaringClass();
    this.pathPattern = new PathPattern(method);
    this.consumesMatcher = new MediaTypeMatcher<Consumes>(method, Consumes.class);
    this.producesMatcher = new MediaTypeMatcher<Produces>(method, Produces.class);
  }

  public boolean matches(final ContainerRequestContext requestContext) {
    if (!httpMethod.value().toUpperCase().equals(requestContext.getMethod().toUpperCase()))
      return false;

    final String path = requestContext.getUriInfo().getPath();
    if (!pathPattern.matches(path))
      return false;

    final MediaType[] accept = MediaTypes.parse(requestContext.getHeaders().get(HttpHeaders.ACCEPT));
    if (!producesMatcher.matches(accept))
      return false;

    final MediaType[] contentType = MediaTypes.parse(requestContext.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    if (!consumesMatcher.matches(contentType))
      return false;

    return true;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object[] getParameters(final Method method, final ContainerRequestContext containerRequestContext, final ContextInjector injectionContext, final EntityProviders entityProviders) throws IOException {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    if (parameterTypes.length == 0)
      return null;

    final Map<String,String> pathParameters = getPathPattern().getParameters(containerRequestContext.getUriInfo().getPath());
    final Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      final Class<?> parameterType = parameterTypes[i];
      final Annotation[] annotations = parameterAnnotations[i];
      if (annotations.length > 0) {
        for (final Annotation annotation : annotations) {
          if (annotation.annotationType() == QueryParam.class) {
            parameters[i] = containerRequestContext.getUriInfo().getQueryParameters().get(((QueryParam)annotation).value());
          }
          else if (annotation.annotationType() == PathParam.class) {
            final String pathParam = ((PathParam)annotation).value();
            parameters[i] = pathParameters.get(pathParam);
          }
          else if (annotation.annotationType() == MatrixParam.class) {
            throw new UnsupportedOperationException();
          }
          else if (annotation.annotationType() == CookieParam.class) {
            throw new UnsupportedOperationException();
          }
          else if (annotation.annotationType() == HeaderParam.class) {
            throw new UnsupportedOperationException();
          }
          else if (annotation.annotationType() == Context.class) {
            parameters[i] = injectionContext.getInjectableObject(parameterType);
          }
          else {
            throw new UnsupportedOperationException("Unexpected annotation type: " + annotation.annotationType().getName() + " on: " + method.getDeclaringClass().getName() + "." + method.getName() + "()");
          }
        }
      }
      else {
        final MessageBodyReader messageBodyReader = entityProviders.getReader(containerRequestContext.getMediaType(), parameterType);
        if (messageBodyReader != null)
          parameters[i] = messageBodyReader.readFrom(parameterType, parameterType.getGenericSuperclass(), parameterType.getAnnotations(), containerRequestContext.getMediaType(), containerRequestContext.getHeaders(), containerRequestContext.getEntityStream());
        else
          throw new WebApplicationException("Could not find MessageBodyReader for type: " + parameterType.getClass().getName());
      }
    }

    return parameters;
  }

  protected boolean checkHeader(final String headerName, final Class<? extends Annotation> annotationClass, final ContainerRequestContext containerRequestContext) {
    final Annotation annotation = getMatcher(annotationClass).getAnnotation();
    if (annotation == null) {
      final String message = "@" + annotationClass.getSimpleName() + " annotation missing for " + method.getDeclaringClass().getName() + "." + Strings.toTitleCase(containerRequestContext.getMethod().toLowerCase()) + "()";
      if (annotationClass == Consumes.class)
        throw new RuntimeException(message);

      logger.warning(message);
      return true;
    }

    final String headerValue = containerRequestContext.getHeaderString(headerName);
    if (headerValue == null || headerValue.length() == 0)
      return logMissingHeaderWarning(headerName, annotationClass);

    final String[] headerValueParts = headerValue.split(",");
    final MediaType[] test = MediaTypes.parse(headerValueParts);

    final String[] annotationValue = annotationClass == Produces.class ? ((Produces)annotation).value() : annotationClass == Consumes.class ? ((Consumes)annotation).value() : null;
    // FIXME: Order matters, and also the q value
    final MediaType[] required = MediaTypes.parse(annotationValue);
    if (MediaTypes.matches(required, test))
      return true;

    return logMissingHeaderWarning(headerName, annotationClass);
  }

  private static void allow(final Annotation securityAnnotation, final ContainerRequestContext containerRequestContext) {
    if (securityAnnotation instanceof PermitAll)
      return;

    if (securityAnnotation instanceof DenyAll)
      throw new ForbiddenException("@DenyAll");

    if (containerRequestContext.getSecurityContext().getUserPrincipal() == null)
      throw new NotAuthorizedException("Unauthorized");

    if (securityAnnotation instanceof RolesAllowed)
      for (final String role : ((RolesAllowed)securityAnnotation).value())
        if (containerRequestContext.getSecurityContext().isUserInRole(role))
          return;

    throw new ForbiddenException("@RolesAllowed(" + Arrays.toString(((RolesAllowed)securityAnnotation).value()) + ")");
  }

  public Object service(final ContainerRequestContext containerRequestContext, final ContextInjector injectionContext, final EntityProviders entityProviders) throws ServletException, IOException {
    allow(securityAnnotation, containerRequestContext);

    try {
      final Object[] parameters = getParameters(method, containerRequestContext, injectionContext, entityProviders);

      final Object object = serviceClass.newInstance();
      return parameters != null ? method.invoke(object, parameters) : method.invoke(object);
    }
    catch (final IllegalAccessException | InstantiationException e) {
      throw new WebApplicationException(e);
    }
    catch (final InvocationTargetException e) {
      // FIXME: Hmm, this is an interesting idea to help reduce the noise in Exceptions from dynamically invoked methods
      if (e.getCause() instanceof WebApplicationException)
        throw (WebApplicationException)e.getCause();

      if (e.getCause() instanceof ServletException)
        throw (ServletException)e.getCause();

      if (e.getCause() instanceof IOException)
        throw (IOException)e.getCause();

      throw new WebApplicationException(e);
    }
    catch (final IllegalArgumentException | IOException e) {
      throw new WebApplicationException(e);
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
}