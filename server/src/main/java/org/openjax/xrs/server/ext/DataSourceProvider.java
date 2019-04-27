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

package org.openjax.xrs.server.ext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

import javax.activation.DataSource;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.openjax.ext.io.Streams;
import org.openjax.ext.util.Objects;

/**
 * JAX-RS 2.1 Section 4.2.4
 */
@Provider
public class DataSourceProvider implements MessageBodyReader<DataSource>, MessageBodyWriter<DataSource> {
  private static final int DEFAULT_BUFFER_SIZE = 65536;

  private final int bufferSize;

  public DataSourceProvider(final int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public DataSourceProvider() {
    this(DEFAULT_BUFFER_SIZE);
  }

  private abstract class ProviderDataSource implements DataSource {
    private final String contentType;
    private final String name;

    private ProviderDataSource(final String contentType, final String name) {
      this.contentType = contentType;
      this.name = name;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Override
  public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return DataSource.class.isAssignableFrom(type);
  }

  @Override
  public DataSource readFrom(final Class<DataSource> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String,String> httpHeaders, final InputStream entityStream) throws IOException {
    final byte[] bytes = new byte[bufferSize];
    final int readCount = entityStream.read(bytes);
    if (readCount < bytes.length) {
      return new ProviderDataSource(mediaType.toString(), "") {
        @Override
        public InputStream getInputStream() throws IOException {
          return new ByteArrayInputStream(bytes, 0, readCount);
        }
      };
    }

    final File file = Files.createTempFile("xrs", null).toFile();
    file.deleteOnExit();
    try (
      final FileOutputStream out = new FileOutputStream(file);
      final FileChannel snk = out.getChannel();
      final ReadableByteChannel src = Channels.newChannel(entityStream);
    ) {
      snk.position(snk.size());
      snk.transferFrom(src, 0, Long.MAX_VALUE);
    }

    return new ProviderDataSource(mediaType.toString(), Objects.identity(entityStream)) {
      @Override
      public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
      }
    };
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return DataSource.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(final DataSource dataSource, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String,Object> httpHeaders, final OutputStream entityStream) throws IOException {
    Streams.pipe(dataSource.getInputStream(), entityStream);
  }
}