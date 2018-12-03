/******************************************************************
 *
 * Copyright 2017 Samsung Electronics All Rights Reserved.
 *
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************/

package org.edge.protocol.opcua.command;

import java.util.concurrent.CompletableFuture;
import org.edge.protocol.opcua.api.common.EdgeMessage;
import org.edge.protocol.opcua.api.common.EdgeMessageType;
import org.edge.protocol.opcua.api.common.EdgeOpcUaCommon;
import org.edge.protocol.opcua.api.common.EdgeResult;
import org.edge.protocol.opcua.api.common.EdgeStatusCode;
import org.edge.protocol.opcua.providers.EdgeAttributeProvider;
import org.edge.protocol.opcua.providers.EdgeServices;
import org.edge.protocol.opcua.providers.services.da.EdgeAttributeService;
import org.edge.protocol.opcua.queue.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provide write function
 */
public class Write implements Command {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * execute with EdgeMessage
   * @param  future
   * @param  msg
   */
  @Override
  public void execute(CompletableFuture<EdgeResult> future, EdgeMessage msg) throws Exception {
    EdgeResult ret = write(msg);
    if (ret != null && ret.getStatusCode() != EdgeStatusCode.STATUS_OK) {
      ErrorHandler.getInstance().addErrorMessage(msg.getRequest().getEdgeNodeInfo(), ret,
          msg.getRequest().getRequestId());
    }
    future.complete(ret);
  }

  /**
   * write with EdgeMessage
   * @param  msg
   * @return EdgeResult
   */
  private EdgeResult write(EdgeMessage msg) throws Exception {
    String serviceName = null;
    EdgeAttributeService service = null;
    EdgeResult ret = null;

    if (msg.getMessageType() == EdgeMessageType.SEND_REQUEST) {
      logger.info("write command - request id = {}", msg.getRequest().getRequestId());
      serviceName = msg.getRequest().getEdgeNodeInfo().getValueAlias();
      EdgeAttributeProvider attributeProvider = EdgeServices.getAttributeProvider(serviceName);
      service = attributeProvider.getAttributeService(serviceName);
    } else if (msg.getMessageType() == EdgeMessageType.SEND_REQUESTS) {
      serviceName = EdgeOpcUaCommon.WELL_KNOWN_GROUP.getValue();
      EdgeAttributeProvider groupServiceProvider = EdgeServices.getAttributeProvider(serviceName);
      service = groupServiceProvider.getGroupService();
    } else {
      return new EdgeResult.Builder(EdgeStatusCode.STATUS_PARAM_INVALID).build();
    }
    try {
      ret = service.write(msg);
    } catch (Exception e) {
      e.printStackTrace();
      ErrorHandler.getInstance().addErrorMessage(msg.getRequest().getEdgeNodeInfo(),
          new EdgeResult.Builder(EdgeStatusCode.STATUS_INTERNAL_ERROR).build(),
          msg.getRequest().getRequestId());
    }
    return ret;
  }
}
