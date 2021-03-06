package de.rwth.idsg.steve.extensions.plugsurfing.service;

import de.rwth.idsg.steve.extensions.plugsurfing.AsyncHttpWrapper;
import de.rwth.idsg.steve.extensions.plugsurfing.Constants;
import de.rwth.idsg.steve.extensions.plugsurfing.dto.ExternalChargePointSelect;
import de.rwth.idsg.steve.extensions.plugsurfing.model.send.response.SessionStartResponse;
import de.rwth.idsg.steve.extensions.plugsurfing.model.send.response.SessionStopResponse;
import de.rwth.idsg.steve.handler.OcppCallback;
import de.rwth.idsg.steve.handler.ocpp12.RemoteStartTransactionResponseHandler;
import de.rwth.idsg.steve.handler.ocpp12.RemoteStopTransactionResponseHandler;
import de.rwth.idsg.steve.ocpp.OcppVersion;
import de.rwth.idsg.steve.service.ChargePointService12_Dispatcher;
import de.rwth.idsg.steve.web.dto.task.ExternalRequestTask;
import lombok.extern.slf4j.Slf4j;
import ocpp.cp._2010._08.RemoteStartStopStatus;
import ocpp.cp._2010._08.RemoteStartTransactionRequest;
import ocpp.cp._2010._08.RemoteStartTransactionResponse;
import ocpp.cp._2010._08.RemoteStopTransactionRequest;
import ocpp.cp._2010._08.RemoteStopTransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Vasil Borozanov <vasil.borozanov@rwth-aachen.de>
 * @since 22.01.2016
 */
@Slf4j
@Service
public class PlugSurfingOcpp12Mediator extends PlugSurfingOcppAbstractMediator {
    private static final OcppVersion VERSION = OcppVersion.V_12;

    @Autowired private ChargePointService12_Dispatcher dispatcher12;

    public void processStartTransaction(String rfid,
                                        ExternalChargePointSelect selectInfo,
                                        AsyncHttpWrapper wrapper) {

        OcppCallback<RemoteStartTransactionResponse> callback = new OcppCallback<RemoteStartTransactionResponse>() {

            @Override
            public void success(RemoteStartTransactionResponse response) {
                RemoteStartStopStatus status = response.getStatus();

                SessionStartResponse ack = new SessionStartResponse();

                //Respond with Http PlugSurfing Codes
                switch (status) {
                    case ACCEPTED:
                        String sessionId = handleAcceptedStartTransaction(rfid, selectInfo);

                        // Fill the values
                        ack.setIsStoppable(true);
                        ack.setSuccess(true);
                        ack.setSessionId(sessionId);
                        break;

                    case REJECTED:
                    default:
                        // Fill the values
                        ack.setSuccess(false);
                        ack.setIsStoppable(false);
                        break;
                }
                finish(ack);
            }

            @Override
            public void failed(String errorMessage) {
                SessionStartResponse ack = new SessionStartResponse();
                ack.setSuccess(false);
                finish(ack);
            }

            private void finish(SessionStartResponse ack) {
                wrapper.finishSuccess(ack);
            }
        };

        RemoteStartTransactionRequest req = new RemoteStartTransactionRequest()
                .withIdTag(rfid)
                .withConnectorId(selectInfo.getConnectorId());

        ExternalRequestTask<RemoteStartTransactionRequest> task = ExternalRequestTask.builder(req)
                .ocppVersion(VERSION)
                .partnerName(Constants.CONFIG.getVendorName())
                .chargePoint(selectInfo.getSelect())
                .build();

        String cbId = selectInfo.getSelect().getChargeBoxId();
        RemoteStartTransactionResponseHandler handler = new RemoteStartTransactionResponseHandler(task, cbId);
        handler.addCallback(callback);

        requestTaskStore.add(task);
        wrapper.startAsync();
        executorService.execute(() -> dispatcher12.remoteStartTransaction(selectInfo.getSelect(), handler));
    }


    public void processStopTransaction(int transactionPk,
                                       ExternalChargePointSelect selectInfo,
                                       AsyncHttpWrapper wrapper) {

        OcppCallback<RemoteStopTransactionResponse> callback = new OcppCallback<RemoteStopTransactionResponse>() {

            @Override
            public void success(RemoteStopTransactionResponse response) {
                RemoteStartStopStatus status = response.getStatus();
                SessionStopResponse ack = new SessionStopResponse();
                switch (status) {
                    case ACCEPTED:
                        ack.setSuccess(true);
                        break;
                    case REJECTED:
                    default:
                        ack.setSuccess(false);
                        break;
                }
                finish(ack);
            }

            @Override
            public void failed(String errorMessage) {
                SessionStopResponse ack = new SessionStopResponse();
                ack.setSuccess(false);
                finish(ack);
            }

            private void finish(SessionStopResponse ack) {
                wrapper.finishSuccess(ack);
            }
        };

        RemoteStopTransactionRequest req = new RemoteStopTransactionRequest()
                .withTransactionId(transactionPk);

        ExternalRequestTask<RemoteStopTransactionRequest> task = ExternalRequestTask.builder(req)
                .ocppVersion(VERSION)
                .partnerName(Constants.CONFIG.getVendorName())
                .chargePoint(selectInfo.getSelect())
                .build();

        String cbId = selectInfo.getSelect().getChargeBoxId();
        RemoteStopTransactionResponseHandler handler = new RemoteStopTransactionResponseHandler(task, cbId);
        handler.addCallback(callback);

        requestTaskStore.add(task);
        wrapper.startAsync();
        executorService.execute(() -> dispatcher12.remoteStopTransaction(selectInfo.getSelect(), handler));
    }
}
