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

import org.edge.protocol.opcua.api.ProtocolManager.DiscoveryCallback;
import org.edge.protocol.opcua.api.ProtocolManager.ReceivedMessageCallback;
import org.edge.protocol.opcua.api.ProtocolManager.StatusCallback;

public class EdgeConfigure {
  private ReceivedMessageCallback recvCallback;
  private StatusCallback statusCallback;
  private DiscoveryCallback discoveryCallback;

  public static class Builder {
    private ReceivedMessageCallback recvCallback = null;
    private StatusCallback statusCallback = null;
    private DiscoveryCallback discoveryCallback = null;

    public Builder() {

    }

    /**
     * set ReceivedMessageCallback
     * @param  cb callback
     * @return this
     */
    public Builder setRecvCallback(ReceivedMessageCallback cb) {
      this.recvCallback = cb;
      return this;
    }

    /**
     * set StatusCallback
     * @param  cb callback
     * @return this
     */
    public Builder setStatusCallback(StatusCallback cb) {
      this.statusCallback = cb;
      return this;
    }

    /**
     * set DiscoveryCallback
     * @param  cb callback
     * @return this
     */
    public Builder setDiscoveryCallback(DiscoveryCallback cb) {
      this.discoveryCallback = cb;
      return this;
    }

    /**
     * create EdgeConfigure instance (builder)
     * @return EdgeConfigure instance
     */
    public EdgeConfigure build() {
      return new EdgeConfigure(this);
    }
  }

  /**
   * constructor
   * @param  builder EdgeConfigure Builder
   */
  private EdgeConfigure(Builder builder) {
    recvCallback = builder.recvCallback;
    statusCallback = builder.statusCallback;
    discoveryCallback = builder.discoveryCallback;
  }

  /**
   * get receiveCallback
   * @return recvCallback
   */
  public ReceivedMessageCallback getRecvCallback() {
    return recvCallback;
  }

  /**
   * get statusCallback
   * @return statusCallback
   */
  public StatusCallback getStatusCallback() {
    return statusCallback;
  }

  /**
   * get discoveryCallback
   * @return discoveryCallback
   */
  public DiscoveryCallback getDiscoveryCallback() {
    return discoveryCallback;
  }
}
