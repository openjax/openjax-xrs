/* Copyright (c) 2016 JetRS
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

package org.jetrs.server.core;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.RuntimeDelegate;

import org.jetrs.common.core.HttpHeadersImpl;
import org.jetrs.common.core.ResponseImpl;
import org.jetrs.common.util.Responses;
import org.jetrs.server.ResourceContext;
import org.libj.util.Locales;

public class ResponseBuilderImpl extends Response.ResponseBuilder implements Cloneable {
  private final ResourceContext resourceContext;
  private final HttpHeadersImpl headers;
  private int status;
  private String reasonPhrase;
  private Object entity;
  private Annotation[] annotations;

  public ResponseBuilderImpl(final ResourceContext resourceContext) {
    this.resourceContext = resourceContext;
    this.headers = new HttpHeadersImpl();
  }

  private ResponseBuilderImpl(final ResponseBuilderImpl copy) {
    this.resourceContext = copy.resourceContext;
    this.headers = copy.headers.clone();
    this.status = copy.status;
    this.reasonPhrase = copy.reasonPhrase;
    this.entity = copy.entity;
    this.annotations = copy.annotations;
    this.cookies = copy.cookies;
  }

  @Override
  public Response build() {
    // FIXME: Need to reset the builder to a "blank state", as is documented in
    // the javadocs of this method
    final Response.StatusType statusType = reasonPhrase != null ? Responses.from(status, reasonPhrase) : Responses.from(status);
    return new ResponseImpl(resourceContext.getProviders(null), resourceContext.getReaderInterceptors(), statusType, headers, cookies, entity, annotations);
  }

  @Override
  public Response.ResponseBuilder status(final int status) {
    this.status = status;
    return this;
  }

  @Override
  public ResponseBuilder status(final int status, final String reasonPhrase) {
    this.status = status;
    this.reasonPhrase = reasonPhrase;
    return this;
  }

  @Override
  public Response.ResponseBuilder entity(final Object entity) {
    this.entity = entity;
    return this;
  }

  @Override
  public Response.ResponseBuilder entity(final Object entity, final Annotation[] annotations) {
    this.entity = entity;
    this.annotations = annotations;
    return this;
  }

  @Override
  public Response.ResponseBuilder allow(final String ... methods) {
    for (final String method : methods)
      headers.add(HttpHeaders.ALLOW, method);

    return this;
  }

  @Override
  public Response.ResponseBuilder allow(final Set<String> methods) {
    for (final String method : methods)
      headers.add(HttpHeaders.ALLOW, method);

    return this;
  }

  @Override
  public Response.ResponseBuilder cacheControl(final CacheControl cacheControl) {
    headers.getMirrorMap().putSingle(HttpHeaders.CACHE_CONTROL, cacheControl);
    return this;
  }

  @Override
  public Response.ResponseBuilder encoding(final String encoding) {
    headers.putSingle(HttpHeaders.CONTENT_ENCODING, encoding);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Response.ResponseBuilder header(final String name, final Object value) {
    if (value == null) {
      headers.getMirrorMap().add(name, null);
      return this;
    }

    final RuntimeDelegate.HeaderDelegate<Object> headerDelegate = (RuntimeDelegate.HeaderDelegate<Object>)RuntimeDelegate.getInstance().createHeaderDelegate(value.getClass());
    headers.getMirrorMap().add(name, headerDelegate != null ? headerDelegate.toString(value) : value.toString());
    return this;
  }

  @Override
  public Response.ResponseBuilder replaceAll(final MultivaluedMap<String,Object> headers) {
    final MultivaluedMap<String,Object> mirrorMap = this.headers.getMirrorMap();
    mirrorMap.clear();
    for (final Map.Entry<String,List<Object>> entry : headers.entrySet())
      mirrorMap.addAll(entry.getKey(), entry.getValue());

    return this;
  }

  @Override
  public Response.ResponseBuilder language(final String language) {
    headers.getMirrorMap().putSingle(HttpHeaders.CONTENT_LANGUAGE, Locales.parse(language));
    headers.putSingle(HttpHeaders.CONTENT_LANGUAGE, language);
    return this;
  }

  @Override
  public Response.ResponseBuilder language(final Locale language) {
    headers.getMirrorMap().putSingle(HttpHeaders.CONTENT_LANGUAGE, language);
    return this;
  }

  @Override
  public Response.ResponseBuilder type(final MediaType type) {
    headers.getMirrorMap().putSingle(HttpHeaders.CONTENT_TYPE, type);
    return this;
  }

  @Override
  public Response.ResponseBuilder type(final String type) {
    headers.putSingle(HttpHeaders.CONTENT_TYPE, type);
    return this;
  }

  @Override
  public Response.ResponseBuilder variant(final Variant variant) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder contentLocation(final URI location) {
    headers.getMirrorMap().putSingle(HttpHeaders.CONTENT_LOCATION, location);
    return this;
  }

  private volatile Map<String,NewCookie> cookies;

  @Override
  public Response.ResponseBuilder cookie(final NewCookie ... cookies) {
    if (this.cookies == null)
      this.cookies = new HashMap<>();

    for (final NewCookie cookie : cookies)
      this.cookies.put(cookie.getName(), cookie);

    return this;
  }

  @Override
  public Response.ResponseBuilder expires(final Date expires) {
    headers.getMirrorMap().putSingle(HttpHeaders.EXPIRES, expires);
    return this;
  }

  @Override
  public Response.ResponseBuilder lastModified(final Date lastModified) {
    headers.getMirrorMap().putSingle(HttpHeaders.LAST_MODIFIED, lastModified);
    return this;
  }

  @Override
  public Response.ResponseBuilder location(final URI location) {
    headers.getMirrorMap().putSingle(HttpHeaders.LOCATION, location);
    return this;
  }

  @Override
  public Response.ResponseBuilder tag(final EntityTag tag) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder tag(final String tag) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder variants(final Variant ... variants) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder variants(final List<Variant> variants) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder links(final Link ... links) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder link(final URI uri, final String rel) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder link(final String uri, final String rel) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Response.ResponseBuilder clone() {
    return new ResponseBuilderImpl(this);
  }
}