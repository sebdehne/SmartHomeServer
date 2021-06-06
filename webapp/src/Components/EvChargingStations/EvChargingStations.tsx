import React, { useEffect, useState } from "react";
import {
    EvChargingStationDataAndConfig,
    EvChargingStationRequest,
    EvChargingStationRequestType
} from "../../Websocket/types/EVChargingStation";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { Container } from "@material-ui/core";
import Header from "../Header";
import { EvChargingStation } from "./EvChargingStation";

export const EvChargingStations = () => {

    const [currentMilliSeconds, setCurrentSeconds] = useState(Date.now());
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [stations, setStations] = useState<EvChargingStationDataAndConfig[]>([]);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentMilliSeconds]);

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.evChargingStationEvents, notify =>
                setStations(notify.evChargingStationEvent!!.chargingStationsDataAndConfig),
            () => WebsocketService.rpc(new RpcRequest(
                RequestType.evChargingStationRequest,
                null,
                null,
                null,
                null,
                new EvChargingStationRequest(
                    EvChargingStationRequestType.getChargingStationsDataAndConfig,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                null,
                null
            )).then(response => setStations(response.evChargingStationResponse!!.chargingStationsDataAndConfig)));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return <Container maxWidth="sm" className="App">
        <Header
            title="EV Charging"
            sending={sending}
        />

        {stations.length > 0 &&
        <div>
            {stations
                .sort((a, b) => {
                    return b.clientConnection.clientId.localeCompare(a.clientConnection.clientId);
                })
                .map(station => (
                <EvChargingStation
                    key={station.clientConnection.clientId}
                    station={station}
                    setCmdResult={setCmdResult}
                    setSending={setSending}
                    setStations={setStations}
                    currentMilliSeconds={currentMilliSeconds}
                />
            ))}
        </div>
        }
        {stations.length === 0 &&
        <h4>Currently no EV charging stations online</h4>
        }


        {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
        {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
    </Container>;
};