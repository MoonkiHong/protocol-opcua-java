/******************************************************************
 *
 * Copyright 2017 Samsung Electronics All Rights Reserved.
 *
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 ******************************************************************/

package org.edge.protocol.opcua.api.common;

import java.util.Random;
import org.edge.protocol.opcua.api.client.EdgeSubRequest;

public class EdgeRequest {
  private EdgeVersatility value;
  private EdgeSubRequest subMsg;
  private final EdgeNodeInfo nodeInfo;
  private final int requestId;
  private final int returnDiagnostic;
  private static Random random = new Random();
  private static int seed = Integer.MAX_VALUE;

  public static class Builder {
    private EdgeVersatility value = null;
    private EdgeSubRequest subMsg = null;
    private final EdgeNodeInfo nodeInfo;
    private int requestId = getRandom();
    private int returnDiagnostic = 0;

    public Builder(EdgeNodeInfo nodeInfo) {
      this.nodeInfo = nodeInfo;
    }

    /**
     * set EdgeVersatility to write
     * @param  value the parameter of EdgeVersatility type
     * @return this
     */
    public Builder setMessage(EdgeVersatility value) {
      this.value = value;
      return this;
    }

    /**
     * set EdgeSubRequest
     * @param  req a request of subscription
     * @return this
     */
    public Builder setSubReq(EdgeSubRequest req) {
      this.subMsg = req;
      return this;
    }

    /**
     * set ReturnDiagnostic
     * @param  diagnostic ReturnDiagnostic
     * @return this
     */
    public Builder setReturnDiagnostic(int diagnostic) {
      this.returnDiagnostic = diagnostic;
      return this;
    }

    /**
     * create EdgeRequest instance (builder)
     * @return EdgeRequest instance
     */
    public EdgeRequest build() {
      return new EdgeRequest(this);
    }
  }

  /**
   * constructor
   * @param  builder EdgeRequest Builder
   */
  private EdgeRequest(Builder builder) {
    value = builder.value;
    nodeInfo = builder.nodeInfo;
    subMsg = builder.subMsg;
    requestId = builder.requestId;
    returnDiagnostic = builder.returnDiagnostic;
  }

  /**
   * get request message to write
   * @return value
   */
  public EdgeVersatility getMessage() {
    return value;
  }

  /**
   * get endpoint
   * @return endpoint
   */
  public EdgeNodeInfo getEdgeNodeInfo() {
    return nodeInfo;
  }

  /**
   * get subscription parameter
   * @return subMsg
   */
  public EdgeSubRequest getSubRequest() {
    return subMsg;
  }

  /**
   * get request id
   * @return requestId
   */
  public int getRequestId() {
    return requestId;
  }

  /**
   * get returnDiagnostic
   * @return returnDiagnostic
   */
  public int getReturnDiagnostic() {
    return returnDiagnostic;
  }

  /**
   * get random integer value
   * @return random value
   */
  private static int getRandom() {
    return random.nextInt(seed);
  }
}
