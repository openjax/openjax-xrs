/* Copyright (c) 2016 OpenJAX
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

package org.jetrs.server.ext;

import java.text.ParseException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.RuntimeDelegate;

import org.jetrs.server.util.MediaTypes;

public class MediaTypeHeaderDelegate implements RuntimeDelegate.HeaderDelegate<MediaType> {
  @Override
  public MediaType fromString(final String value) {
    try {
      return MediaTypes.parse(value);
    }
    catch (final ParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public String toString(final MediaType value) {
    return MediaTypes.toString(value);
  }
}