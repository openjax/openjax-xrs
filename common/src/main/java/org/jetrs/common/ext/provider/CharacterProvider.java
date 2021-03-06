/* Copyright (c) 2019 OpenJAX
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

package org.jetrs.common.ext.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.jetrs.common.util.MediaTypes;
import org.jetrs.common.util.MultivaluedMaps;
import org.jetrs.common.util.ProviderUtil;

/**
 * JAX-RS 2.1 Section 4.2.4
 */
@Provider
@Consumes("text/plain")
@Produces("text/plain")
public class CharacterProvider implements MessageBodyReader<Character>, MessageBodyWriter<Character> {
  @Override
  public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return type == Character.class && MediaTypes.TEXT_PLAIN.isCompatible(mediaType);
  }

  @Override
  public Character readFrom(final Class<Character> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String,String> httpHeaders, final InputStream entityStream) throws IOException, BadRequestException {
    if (MultivaluedMaps.getFirstOrDefault(httpHeaders, HttpHeaders.CONTENT_LENGTH, Long.MAX_VALUE, Long::parseLong) == 0)
      return null;

    final String data = ProviderUtil.toString(entityStream, mediaType.getParameters().get(MediaType.CHARSET_PARAMETER));
    if (data.length() > 1)
      throw new BadRequestException();

    return data.length() == 0 ? null : data.charAt(0);
  }

  @Override
  public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return type == Character.class;
  }

  @Override
  public long getSize(final Character t, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(final Character t, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String,Object> httpHeaders, final OutputStream entityStream) throws IOException, WebApplicationException {
    entityStream.write(ProviderUtil.toBytes(t, mediaType));
  }
}