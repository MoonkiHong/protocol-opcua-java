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

package org.edge.protocol.opcua.providers.services.sub;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemModifyRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.edge.protocol.opcua.api.ProtocolManager;
import org.edge.protocol.opcua.api.client.EdgeResponse;
import org.edge.protocol.opcua.api.client.EdgeSubRequest;
import org.edge.protocol.opcua.api.common.EdgeNodeInfo;
import org.edge.protocol.opcua.api.common.EdgeNodeType;
import org.edge.protocol.opcua.api.common.EdgeEndpointInfo;
import org.edge.protocol.opcua.api.common.EdgeMessage;
import org.edge.protocol.opcua.api.common.EdgeMessageType;
import org.edge.protocol.opcua.api.common.EdgeNodeIdentifier;
import org.edge.protocol.opcua.api.common.EdgeRequest;
import org.edge.protocol.opcua.api.common.EdgeResult;
import org.edge.protocol.opcua.api.common.EdgeStatusCode;
import org.edge.protocol.opcua.api.common.EdgeVersatility;
import org.edge.protocol.opcua.queue.ErrorHandler;
import org.edge.protocol.opcua.session.EdgeSessionManager;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableList;

public class EdgeMonitoredItemService {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final Map<String, EdgeSubscription> subList =
      new ConcurrentHashMap<String, EdgeSubscription>();
  private static EdgeMonitoredItemService service = null;
  private static Object lock = new Object();
  private final AtomicLong clientHandles = new AtomicLong(1L);

  private EdgeMonitoredItemService() {}

  /**
   * get EdgeMonitoredItemService Instance
   * 
   * @return EdgeMonitoredItemService Instance
   */
  public static EdgeMonitoredItemService getInstance() {
    synchronized (lock) {
      if (null == service) {
        service = new EdgeMonitoredItemService();
      }
      return service;
    }
  }

  /**
   * add subscribers to the list of subscription
   * 
   * @param endpoint
   * @param UaSubscription
   * @param EdgeSubRequest
   * @return void
   */
  private void addSubscription(String endpointUri, UaSubscription sub, EdgeSubRequest req) {
    if (true == subList.containsKey(endpointUri.toString())) {
      subList.remove(endpointUri.toString());
    }
    subList.put(endpointUri, new EdgeSubscription.Builder(sub).setSubRequest(req).build());
  }

  /**
   * Get a subscriber from the list of subscription
   * 
   * @param endpoint
   * @return UaSubscription
   */
  private UaSubscription getSubscription(String endpointUri) {
    return subList.get(endpointUri).getUaSubscription();
  }

  /**
   * Get a subscription request from the list of subscription
   * 
   * @param endpoint
   * @return EdgeSubRequest
   */
  private EdgeSubRequest getSubRequest(String endpointUri) {
    return subList.get(endpointUri).getSubRequest();
  }

  /**
   * subscription according for sub-request to endpoint
   * 
   * @param request Subscription Request Parameters Subscription for opc-ua can be set such as
   *        samplingInterval, publishingInterval and etc. first monitored message will be sent to
   *        both onMonitoredMessage and onResponseMessage. and error message will be checked in
   *        onErrorMessage Callback.
   * @param nodeInfo target node information
   * @param epInfo target endpoint
   * @return result
   */
  public EdgeResult subscription(EdgeRequest request, EdgeNodeInfo nodeInfo,
      EdgeEndpointInfo epInfo) throws Exception {
    EdgeSubRequest req = request.getSubRequest();
    logger.debug("subscribeDataChangeNotification : pub interval={}, sampling interver={}",
        req.getPublishingInterval(), req.getSamplingInterval());

    UaSubscription subscription = null;
    // create a subscription and a monitored item
    if (req.getSubType() == EdgeNodeIdentifier.Edge_Create_Sub) {
      CompletableFuture<UaSubscription> sub = null;

      if (req.getCTTFlag() == false) {
        sub = EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri())
            .getClientInstance().getSubscriptionManager()
            .createSubscription(req.getPublishingInterval()).thenApply(obj -> {

              return obj;
            }).exceptionally(e -> {
              Optional.ofNullable(nodeInfo).ifPresent(endpoint -> {
                logger.error("error message={}", e.getMessage());
                ErrorHandler.getInstance().addErrorMessage(nodeInfo,
                    new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                    new EdgeVersatility.Builder(e.getMessage()).build(), request.getRequestId());
              });
              return null;
            });

      } else {
        // since subscription parameter is supported, it is needed to run for CTT
        // Subscription
        sub = EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri())
            .getClientInstance().getSubscriptionManager()
            .createSubscription(req.getPublishingInterval(), uint(req.getLifetimeCount()),
                uint(req.getMaxKeepAliveCount()), uint(req.getMaxNotificationsPerPublish()),
                req.getPublishingFlag(), ubyte(req.getPriority()))
            .thenApply(obj -> {

              return obj;
            }).exceptionally(e -> {
              Optional.ofNullable(nodeInfo).ifPresent(endpoint -> {
                logger.error("error message={}", e.getMessage());
                ErrorHandler.getInstance().addErrorMessage(nodeInfo,
                    new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                    new EdgeVersatility.Builder(e.getMessage()).build(), request.getRequestId());
              });
              return null;
            });
      }

      EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri()).getClientInstance()
          .getSubscriptionManager()
          .addSubscriptionListener(new UaSubscriptionManager.SubscriptionListener() {
            @Override
            public void onKeepAlive(UaSubscription subscription, DateTime publishTime) {
              logger.debug("onKeepAlive");
            }

            @Override
            public void onStatusChanged(UaSubscription subscription, StatusCode status) {
              logger.debug("onStatusChanged status={}", status);
            }

            @Override
            public void onPublishFailure(UaException exception) {
              logger.debug("onPublishFailure status={}", exception.getMessage());
              ErrorHandler.getInstance().addErrorMessage(nodeInfo,
                  new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                  new EdgeVersatility.Builder(exception.getMessage()).build(),
                  request.getRequestId());
            }

            @Override
            public void onNotificationDataLost(UaSubscription subscription) {
              logger.debug("onNotificationDataLost");
              ErrorHandler.getInstance().addErrorMessage(nodeInfo,
                  new EdgeResult.Builder(EdgeStatusCode.STATUS_SUB_DATA_LOSS).build(),
                  request.getRequestId());
            }

            @Override
            public void onSubscriptionTransferFailed(UaSubscription subscription,
                StatusCode statusCode) {
              logger.debug("onSubscriptionTransferFailed status={}", statusCode);
            }
          });
      subscription = sub.get();
      subscription.addNotificationListener(new UaSubscription.NotificationListener() {
        boolean isFirstData = true;

        public void onNotificationError(UaSubscription subscription, String reason) {
          logger.debug("onNotificationError = {}", reason);
        }

        @Override
        public void onKeepAliveNotification(UaSubscription subscription, DateTime publishTime) {
          logger.debug("onKeepAliveNotification = {}", publishTime);
        }

        @Override
        public void onStatusChangedNotification(UaSubscription subscription, StatusCode status) {
          logger.debug("onStatusChangedNotification status={}", status);
        }

        @Override
        public void onDataChangeNotification(UaSubscription subscription,
            ImmutableList<Tuple2<UaMonitoredItem, DataValue>> itemValues, DateTime publishTime) {

          if (req.getCTTFlag() == true) {
            EdgeStatusCode code =
                checkNotificationMessage(itemValues, subscription, publishTime, epInfo);
            if (EdgeStatusCode.STATUS_OK != code) {
              logger.error("error edge status code={}", code);
              ErrorHandler.getInstance().addErrorMessage(nodeInfo,
                  new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                  new EdgeVersatility.Builder(code.getDescription()).build(),
                  request.getRequestId());
            }
          }

          for (Tuple2<UaMonitoredItem, DataValue> itemValue : itemValues) {
            UaMonitoredItem item = itemValue.v1();
            DataValue value = itemValue.v2();
            logger.debug("=======================notification message=============================");
            Timestamp stamp = new Timestamp(System.currentTimeMillis());
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss.SSS");
            logger.debug(
                "onNoti recvTime={}, publishTime={}, value={}, ep={}, URI = {}, item size={}",
                stamp, dateFormat.format(publishTime.getJavaDate()), value.getValue(),
                nodeInfo.getValueAlias(), epInfo.getEndpointUri(), itemValues.size());
            logger.debug("----------------------------UaMonitoredItem data------------------------");
            logger.debug(
                "onNoti MonitoredItemId={}, MonitoringMode={} RequestedQueueSize={}, "
                    + "RequestedSamplingInterval={}, ",
                item.getMonitoredItemId(), item.getMonitoringMode(), item.getRequestedQueueSize(),
                item.getRequestedSamplingInterval());
            logger.debug(
                "onNoti RevisedQueueSize={}, RevisedSampling={} StatusCode={}, ReadValueId={}, ",
                item.getRevisedQueueSize(), item.getRevisedSamplingInterval(), item.getStatusCode(),
                item.getReadValueId());
            logger.debug("----------------------------UaSubscription data------------------------");
            logger.debug("onNoti SubscriptionId={}, MaxNotificationsPerPublish={}, Priority={} ",
                subscription.getSubscriptionId(), subscription.getMaxNotificationsPerPublish(),
                subscription.getPriority());

            logger.debug("onNoti RequestedPublishingInterval={}, getRevisedPublishingInterval={}",
                subscription.getRequestedPublishingInterval(),
                subscription.getRevisedPublishingInterval());

            logger.debug(
                "onNoti RequestedLifetimeCount={}, RevisedLifetimeCount={} / "
                    + "RequestedMaxKeepAliveCount={} RevisedMaxKeepAliveCount={}",
                subscription.getRequestedLifetimeCount(), subscription.getRevisedLifetimeCount(),
                subscription.getRequestedMaxKeepAliveCount(),
                subscription.getRevisedMaxKeepAliveCount());

            if (isFirstData) {
              EdgeMessage inputData = new EdgeMessage.Builder(epInfo)
                  .setResponses(
                      newArrayList(new EdgeResponse.Builder(nodeInfo, request.getRequestId())
                          .setMessage(
                              new EdgeVersatility.Builder(value.getValue().getValue()).build())
                          .build()))
                  .setMessageType(EdgeMessageType.GENERAL_RESPONSE).build();
              ProtocolManager.getProtocolManagerInstance().getRecvDispatcher().putQ(inputData);
              isFirstData = false;
            }

            EdgeMessage inputData = new EdgeMessage.Builder(epInfo)
                .setResponses(
                    newArrayList(new EdgeResponse.Builder(nodeInfo, request.getRequestId())
                        .setDateTime(publishTime)
                        .setMessage(
                            new EdgeVersatility.Builder(value.getValue().getValue()).build())
                        .build()))
                .setMessageType(EdgeMessageType.REPORT).build();
            ProtocolManager.getProtocolManagerInstance().getRecvDispatcher().putQ(inputData);
          }
        }
      });
      addSubscription(epInfo.getEndpointUri(), subscription, req);

    } else if (req.getSubType() == EdgeNodeIdentifier.Edge_Modify_Sub) { // modify sub
      subscription = getSubscription(epInfo.getEndpointUri());
      CompletableFuture<UaSubscription> sub =
          modifySubscription(epInfo, nodeInfo, subscription, request);
      setPublishMode(epInfo, nodeInfo, newArrayList(sub.get().getSubscriptionId()), request);
      addSubscription(epInfo.getEndpointUri(), sub.get(), req);

    } else if (req.getSubType() == EdgeNodeIdentifier.Edge_Delete_Sub) { // delete sub
      subscription = getSubscription(epInfo.getEndpointUri());
      deleteSubscriptions(epInfo, nodeInfo, newArrayList(subscription.getSubscriptionId()), request)
          .thenApply(result -> {
            return result;
          });
    } else if (req.getSubType() == EdgeNodeIdentifier.Edge_Republish_Sub) { // delete sub
      logger.info("republish");
      subscription = getSubscription(epInfo.getEndpointUri());
      EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri()).getClientInstance()
          .republish(subscription.getSubscriptionId(), uint(clientHandles.getAndIncrement()))
          .thenApply(res -> {
            logger.info("repub - response ={} ", res.getResponseHeader().getServiceResult());
            return res;
          }).exceptionally(e -> {
            Optional.ofNullable(nodeInfo).ifPresent(endpoint -> {
              logger.info("repub error message={}", e.getMessage());
            });
            return null;
          });
    }

    RunMonitoredItemService(request, nodeInfo, subscription);
    return new EdgeResult.Builder(
        subscription == null ? EdgeStatusCode.STATUS_ERROR : EdgeStatusCode.STATUS_OK).build();
  }

  /**
   * execute monitored item service
   * 
   * @param request request of monitored item
   * @param ep node information of
   * @param subscription Uasubscription
   * @return void
   */
  private void RunMonitoredItemService(EdgeRequest request, EdgeNodeInfo ep,
      UaSubscription subscription) throws Exception {
    logger.debug("MonitoredItemService is ran");

    if (ep.getEdgeNodeID() == null) {
      logger.error("EdgeNodeId is empty");
      return;
    }

    EdgeSubRequest req = request.getSubRequest();
    NodeId id = null;
    if (ep.getEdgeNodeID().getEdgeNodeType() == EdgeNodeType.INTEGER) {
      id = new NodeId(ep.getEdgeNodeID().getNameSpace(),
          ep.getEdgeNodeID().getEdgeNodeIdentifier().value());

    } else {
      id = new NodeId(ep.getEdgeNodeID().getNameSpace(), ep.getEdgeNodeID().getEdgeNodeUri());
    }
    logger.debug("Create NodeId={}", id);

    ReadValueId readValueId =
        new ReadValueId(id, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

    MonitoringParameters parameters = new MonitoringParameters(uint(1), // client
                                                                        // handle
        req.getSamplingInterval(), // sampling interval
        null, // no (default) filtersubscribeDataChangeNotification
        uint(req.getQueueSize()), // queue size
        true); // discard oldest

    if (req.getSubType() == EdgeNodeIdentifier.Edge_Modify_Sub) {

      logger.debug("MonitoredItemModifyRequest={}", req.getSamplingInterval());
      ImmutableList<UaMonitoredItem> itemValues = subscription.getMonitoredItems().asList();

      for (UaMonitoredItem itemValue : itemValues) {
        logger.debug("item values size = {}", itemValues.size());
        MonitoredItemModifyRequest monitoredItemRequest =
            new MonitoredItemModifyRequest(itemValue.getMonitoredItemId(), parameters);

        subscription
            .modifyMonitoredItems(TimestampsToReturn.Both, newArrayList(monitoredItemRequest))
            .thenApply(monitoredItems -> {
              logger.debug("MonitoredItemModifyRequest item size={}", monitoredItems.size());
              // TODO UA-milo should be updated with List<UaMonitoredItem> return value.
              // checkMonitoredItemErrorStatus(monitoredItems, request);
              return monitoredItems;
            }).exceptionally(e -> {
              logger.error("error type : {}", e.getMessage());
              callErrorMessageCB(request, EdgeStatusCode.STATUS_ERROR);
              return null;
            });

      }
    } else if (req.getSubType() == EdgeNodeIdentifier.Edge_Create_Sub) {

      logger.debug("MonitoredItemCreateRequest : sampling interval={}", req.getSamplingInterval());
      MonitoredItemCreateRequest monitoredItemRequest =
          new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);

      subscription.createMonitoredItems(TimestampsToReturn.Both, newArrayList(monitoredItemRequest))
          .thenApply(monitoredItems -> {
            logger.debug("MonitoredItemCreateRequest item size={}", monitoredItems.size());
            checkMonitoredItemErrorStatus(monitoredItems, request);
            return monitoredItems;
          }).exceptionally(e -> {
            logger.error("error type : {}", e.getMessage());
            callErrorMessageCB(request, EdgeStatusCode.STATUS_ERROR);
            return null;
          });
    } else if (req.getSubType() == EdgeNodeIdentifier.Edge_Delete_Sub) {

      logger.debug("deleteMonitoredItems");
      ImmutableList<UaMonitoredItem> itemValues = subscription.getMonitoredItems().asList();
      List<UaMonitoredItem> items = new ArrayList<UaMonitoredItem>();

      for (UaMonitoredItem itemValue : itemValues) {
        items.add(itemValue);
      }

      subscription.deleteMonitoredItems(items).thenApply(statusCodes -> {
        logger.debug("deleteMonitoredItems size={}", statusCodes.size());
        return statusCodes;
      }).exceptionally(e -> {
        logger.debug("error type : {}", e.getMessage());
        callErrorMessageCB(request, EdgeStatusCode.STATUS_ERROR);
        return null;
      });

    } else {
      logger.debug("MonitoredItem type = {}", req.getSubType());
    }
  }

  /**
   * verify execute monitored
   * 
   * @param monitoredItems list of monitored item
   * @param request request of monitored Item
   * @return void
   */
  private void checkMonitoredItemErrorStatus(List<UaMonitoredItem> monitoredItems,
      EdgeRequest req) {
    if (monitoredItems.size() == 0) {
      logger.debug("MonitoredItem size = {}", monitoredItems.size());
    }
    int idx = 0;
    boolean isVisited = false;
    int errStatusCodeCnt = 0;
    int errSamplingCnt = 0;
    StatusCode errorCode = StatusCode.BAD;
    HashSet<UInteger> visitedId = new HashSet<UInteger>();

    for (UaMonitoredItem item : monitoredItems) {
      logger.debug("MonitoredItem id = {}", item.getMonitoredItemId());
      logger.debug("MonitoredItem queue size = {} -> {}", item.getRequestedQueueSize(),
          item.getRevisedQueueSize());
      logger.debug("MonitoredItem sampling = {} -> {}", item.getRequestedSamplingInterval(),
          item.getRevisedSamplingInterval());

      if (item.getStatusCode().isGood() != true) {
        if (idx == 0) {
          callErrorMessageCB(req, EdgeStatusCode.STATUS_ERROR, item.getStatusCode().toString());
        }

        if (isVisited == false) {
          errorCode = item.getStatusCode();
          isVisited = true;
        }
        if (errorCode.getValue() == item.getStatusCode().getValue()) {
          errStatusCodeCnt++;
        }
        logger.debug("MonitoredItem code = {}", item.getStatusCode());

      } else {
        // check sampling interval
        if (item.getRequestedSamplingInterval() != item.getRevisedSamplingInterval()
            || item.getRevisedSamplingInterval() < 0 || item.getRevisedSamplingInterval() == 0) {
          errSamplingCnt++;
          if (item.getRequestedSamplingInterval() > item.getRevisedSamplingInterval()) {
            callErrorMessageCB(req, EdgeStatusCode.STATUS_MONITOR_SAMPLING_INVERVAL_INVALID,
                "revised value is larger than request");
          } else if (item.getRequestedSamplingInterval() > item.getRevisedSamplingInterval()) {
            callErrorMessageCB(req, EdgeStatusCode.STATUS_MONITOR_SAMPLING_INVERVAL_INVALID,
                "revised value is smaller than request");
          } else {
            callErrorMessageCB(req, EdgeStatusCode.STATUS_MONITOR_SAMPLING_INVERVAL_INVALID);
          }
        }

        // check queue size
        if (item.getRequestedQueueSize().intValue() != item.getRevisedQueueSize().intValue()
            || item.getRevisedQueueSize().intValue() == 0) {
          if (item.getRequestedQueueSize().intValue() < item.getRevisedQueueSize().intValue()) {
            callErrorMessageCB(req, EdgeStatusCode.STATUS_MONITOR_QUEUE_SIZE_INVALID,
                "revised value is larger than request");
          } else {
            callErrorMessageCB(req, EdgeStatusCode.STATUS_MONITOR_QUEUE_SIZE_INVALID);
          }
        }

        if (idx == 0 && item.getMonitoredItemId().intValue() == 0) {
          callErrorMessageCB(req, EdgeStatusCode.STATUS_ERROR, "item ID is invalid");
          continue;
        } else if (visitedId.contains(item.getMonitoredItemId())) {
          callErrorMessageCB(req, EdgeStatusCode.STATUS_ERROR,
              "duplicate item ID " + idx + " is used");
        }
        visitedId.add(item.getMonitoredItemId());
      }
      idx++;
    }

    if (monitoredItems.size() == errStatusCodeCnt || monitoredItems.size() == errSamplingCnt) {
      callErrorMessageCB(req, EdgeStatusCode.STAUTS_MONITOR_ALL_ITEMS_ERROR, errorCode.toString());
    }
  }

  /**
   * Set modes to publish
   * 
   * @param ep endpoint
   * @param nodeInfo node Information
   * @param list of subscription id
   * @param edgeRequest
   * @return void
   */
  private void setPublishMode(EdgeEndpointInfo ep, EdgeNodeInfo nodeInfo,
      List<UInteger> subscriptionIds, EdgeRequest req) {
    EdgeSessionManager.getInstance().getSession(ep.getEndpointUri()).getClientInstance()
        .setPublishingMode(req.getSubRequest().getPublishingFlag(), subscriptionIds)
        .thenApply(response -> {
          logger.debug("setPublishingMode Service Result={}",
              response.getResponseHeader().getServiceResult());

          if (response.getResults().length == 0) {
            logger.error("result is empty");
            ErrorHandler.getInstance().addErrorMessage(ep, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(
                    EdgeStatusCode.STATUS_SUB_SETPUBLISH_EMPTY_RESULT.getDescription()).build(),
                req.getRequestId());
          }

          for (StatusCode code : response.getResults()) {
            logger.debug("setPublishingMode item result={}", code);
          }
          return response;
        }).exceptionally(e -> {
          logger.error("setPublishingMode error message={}", e.getMessage());
          return null;
        });
  }

  /**
   * provide api to change subscription
   * 
   * @param ep endpoint
   * @param nodeInfo node Information
   * @param uaSubscription
   * @param edgeRequest
   * @return CompletableFuture<UaSubscription>
   */
  private CompletableFuture<UaSubscription> modifySubscription(EdgeEndpointInfo epInfo,
      EdgeNodeInfo nodeInfo, UaSubscription sub, EdgeRequest req) {
    return EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri()).getClientInstance()
        .getSubscriptionManager()
        .modifySubscription(sub.getSubscriptionId(), req.getSubRequest().getPublishingInterval(),
            uint(req.getSubRequest().getLifetimeCount()),
            uint(req.getSubRequest().getMaxKeepAliveCount()),
            uint(req.getSubRequest().getMaxNotificationsPerPublish()),
            ubyte(req.getSubRequest().getPriority()))
        .thenApply(obj -> {
          return obj;
        }).exceptionally(e -> {
          Optional.ofNullable(epInfo).ifPresent(endpoint -> {
            logger.error("modifySub error message={}", e.getMessage());
            ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(e.getMessage()).build(), req.getRequestId());
          });
          return null;
        });
  }

  /**
   * delete multiple subscriptions from the list of subscription
   * 
   * @param ep endpoint
   * @param nodeInfo node Information
   * @param list of subscription id
   * @param edgeRequest
   * @return CompletableFuture<EdgeResult>
   */
  private CompletableFuture<EdgeResult> deleteSubscriptions(EdgeEndpointInfo epInfo,
      EdgeNodeInfo nodeInfo, List<UInteger> subscriptionIds, EdgeRequest req) {
    return EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri()).getClientInstance()
        .deleteSubscriptions(subscriptionIds).thenApply(response -> {
          StatusCode serviceResult = response.getResponseHeader().getServiceResult();
          logger.info("delete sub message={}", serviceResult);

          if (response.getResults().length < subscriptionIds.size() && serviceResult.isGood()) {
            logger.debug("delete sub result : result is decreased than requests");
            ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(
                    EdgeStatusCode.STATUS_SUB_DELETE_ITEM_DECREASE.getDescription()).build(),
                req.getRequestId());

          } else if (response.getResults().length > subscriptionIds.size()
              && serviceResult.isGood()) {
            logger.debug("delete sub result : result is increased than requests");
            ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(
                    EdgeStatusCode.STATUS_SUB_DELETE_ITEM_INCREASE.getDescription()).build(),
                req.getRequestId());

          } else {
            for (StatusCode code : response.getResults()) {
              logger.debug("delete sub result={}", code);
              if (code.isGood() != true) {
                EdgeStatusCode statusCode = convertStatusCode(code.getValue());
                ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                    new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                    new EdgeVersatility.Builder(statusCode.getDescription()).build(),
                    req.getRequestId());
              }
            }
          }
          return new EdgeResult.Builder(EdgeStatusCode.STATUS_OK).build();
        }).exceptionally(e -> {
          Optional.ofNullable(epInfo).ifPresent(endpoint -> {
            logger.debug("delete sub error message={}", e.getMessage());
            ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(e.getMessage()).build(), req.getRequestId());
          });
          return new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build();
        });
  }

  /**
   * @fn EdgeStatusCode convertStatusCode(long code)
   * @brief convert status code for edge computing platform
   * @prarm [in] code
   * @return EdgeStatusCode
   */
  private EdgeStatusCode convertStatusCode(long code) {
    EdgeStatusCode statusCode = EdgeStatusCode.STATUS_ERROR;
    if (StatusCodes.Bad_NothingToDo == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_NOTHING_TO_DO;
    } else if (StatusCodes.Bad_TooManyOperations == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_TOO_MANY_OPERATION;
    } else if (StatusCodes.Bad_SubscriptionIdInvalid == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_ID_INVALID;
    } else if (StatusCodes.Bad_InternalError == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_LIB_INTERNAL_ERROR;
    } else if (StatusCodes.Bad_SequenceNumberUnknown == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_SEQUENCE_NUMBER_UNKNOWN;
    } else if (StatusCodes.Bad_SequenceNumberInvalid == code) {
      statusCode = EdgeStatusCode.STATUS_SUB_SEQUENCE_NUMBER_INVALID;
    }
    return statusCode;
  }

  /**
   * delete a subscription from the list of subscription
   * 
   * @param ep endpoint
   * @param nodeInfo node Information
   * @param list of subscription id
   * @param edgeRequest
   * @return CompletableFuture<EdgeResult>
   */
  private CompletableFuture<EdgeResult> deleteSubscription(EdgeEndpointInfo epInfo,
      EdgeNodeInfo nodeInfo, UInteger subscriptionId, EdgeRequest req) {
    return EdgeSessionManager.getInstance().getSession(epInfo.getEndpointUri()).getClientInstance()
        .getSubscriptionManager().deleteSubscription(subscriptionId).thenApply(sub -> {
          return new EdgeResult.Builder(EdgeStatusCode.STATUS_OK).build();
        }).exceptionally(e -> {
          Optional.ofNullable(epInfo).ifPresent(endpoint -> {
            logger.error("delete sub error message={}", e.getMessage());
            ErrorHandler.getInstance().addErrorMessage(epInfo, nodeInfo,
                new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build(),
                new EdgeVersatility.Builder(e.getMessage()).build(), req.getRequestId());
          });
          return new EdgeResult.Builder(EdgeStatusCode.STATUS_ERROR).build();
        });
  }

  /**
   * delete a subscription from the list of subscription
   * 
   * @param ImmutableList<Tuple2<UaMonitoredItem, DataValue>> itemValues
   * @param UaSubscription sub
   * @param DateTime publishTime
   * @param epInfo EdgeEndpointInfo
   * @return EdgeStatusCode
   */
  private EdgeStatusCode checkNotificationMessage(
      ImmutableList<Tuple2<UaMonitoredItem, DataValue>> itemValues, UaSubscription sub,
      DateTime publishTime, EdgeEndpointInfo epInfo) {

    if (sub == null) {
      return EdgeStatusCode.STATUS_ERROR;
    }

    if (sub.getRequestedPublishingInterval() != sub.getRevisedPublishingInterval()) {
      return EdgeStatusCode.STATUS_SUB_PUB_INTERVAL_DIFFERENCE;
    } else if ((sub.getRevisedLifetimeCount() != sub.getRequestedLifetimeCount())
        || (sub.getRevisedLifetimeCount()
            .longValue() < sub.getRequestedMaxKeepAliveCount().longValue() * 3)) {
      return EdgeStatusCode.STATUS_SUB_LIFETIME_DIFFERENCE;
    } else if (sub.getRevisedMaxKeepAliveCount() != sub.getRequestedMaxKeepAliveCount()) {
      return EdgeStatusCode.STATUS_SUB_MAX_KEEPALIVE_DIFFERENCE;
    } else if (true == publishTime.getJavaDate().after(new Date())) {
      return EdgeStatusCode.STATUS_SUB_NOTIFICATION_TIME_INVALID;
    } else if (itemValues.size() > getSubRequest(epInfo.getEndpointUri())
        .getMaxNotificationsPerPublish()) {
      return EdgeStatusCode.STATUS_SUB_MAX_NOTIFICATION_NOT_MATCH;
    }
    return EdgeStatusCode.STATUS_OK;
  }

  /**
   * Call error message callback with error reason
   * 
   * @param EdgeRequest request
   * @param EdgeStatusCode code
   * @param String reason
   * @return void
   */
  private void callErrorMessageCB(EdgeRequest req, EdgeStatusCode code, String reason) {
    ErrorHandler.getInstance().addErrorMessage(req.getEdgeNodeInfo(),
        new EdgeResult.Builder(code).build(), new EdgeVersatility.Builder(reason).build(),
        req.getRequestId());
  }

  /**
   * Call error message callback
   * 
   * @param EdgeRequest request
   * @param EdgeStatusCode code
   * @return void
   */
  private void callErrorMessageCB(EdgeRequest req, EdgeStatusCode code) {
    ErrorHandler.getInstance().addErrorMessage(req.getEdgeNodeInfo(),
        new EdgeResult.Builder(code).build(),
        new EdgeVersatility.Builder(code.getDescription()).build(), req.getRequestId());
  }
}
