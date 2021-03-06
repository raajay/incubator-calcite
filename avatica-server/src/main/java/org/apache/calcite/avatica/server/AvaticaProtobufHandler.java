/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.avatica.server;

import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.avatica.remote.ProtobufHandler;
import org.apache.calcite.avatica.remote.ProtobufTranslationImpl;
import org.apache.calcite.avatica.remote.Service;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Jetty handler that executes Avatica JSON request-responses.
 */
public class AvaticaProtobufHandler extends AbstractHandler {

  private final ProtobufHandler pbHandler;

  public AvaticaProtobufHandler(Service service) {
    this.pbHandler = new ProtobufHandler(service, new ProtobufTranslationImpl());
  }

  public void handle(String target, Request baseRequest,
      HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("application/octet-stream;charset=utf-8");
    response.setStatus(HttpServletResponse.SC_OK);
    if (request.getMethod().equals("POST")) {
      byte[] requestBytes;
      try (ServletInputStream inputStream = request.getInputStream()) {
        requestBytes = AvaticaUtils.readFullyToBytes(inputStream);
      }

      byte[] responseBytes = pbHandler.apply(requestBytes);

      baseRequest.setHandled(true);
      response.getOutputStream().write(responseBytes);
    }
  }

}

// End AvaticaProtobufHandler.java
