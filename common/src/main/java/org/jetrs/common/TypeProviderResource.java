/* Copyright (c) 2018 JetRS
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

package org.jetrs.common;

import java.lang.reflect.InvocationTargetException;

public abstract class TypeProviderResource<T> extends ProviderResource<T> {
  private final Class<?> type;

  public TypeProviderResource(final Class<T> clazz, final T singleton, final Class<?> type) throws IllegalAccessException, InstantiationException, InvocationTargetException {
    super(clazz, singleton);
    this.type = type;
  }

  public Class<?> getType() {
    return this.type;
  }
}