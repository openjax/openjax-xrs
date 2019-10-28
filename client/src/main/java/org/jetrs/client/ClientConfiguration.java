/* Copyright (c) 2019 JetRS
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

package org.jetrs.client;

import javax.ws.rs.RuntimeType;

import org.jetrs.common.core.ConfigurationImpl;

public class ClientConfiguration extends ConfigurationImpl {
  private static final long serialVersionUID = -1380692486865663993L;

  @Override
  public RuntimeType getRuntimeType() {
    return RuntimeType.CLIENT;
  }

  @Override
  public ClientConfiguration clone() {
    return (ClientConfiguration)super.clone();
  }
}