import React, { useEffect, useState } from "react";
import {
    EvChargingStationDataAndConfig,
    EvChargingStationRequest,
    EvChargingStationRequestType
} from "../../Websocket/types/EVChargingStation";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { Button, CircularProgress, Container, Paper, Typography } from "@material-ui/core";
import ConnectionStatusComponent from "../ConnectionStatus";
import { useHistory } from "react-router-dom";
import { EvChargingStation } from "./EvChargingStation";

export const EvChargingStations = () => {


    let history = useHistory();
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [stations, setStations] = useState<EvChargingStationDataAndConfig[]>([]);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds]);

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
                )
            )).then(response => setStations(response.evChargingStationResponse!!.chargingStationsDataAndConfig)));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return <Container maxWidth="sm" className="App">
        <Paper>
            <ConnectionStatusComponent/>
            <Button variant="text" color="secondary" onClick={() => {
                history.push("/")
            }}>
                Home
            </Button>

            <Typography>
                <h3 style={{ display: "inline" }}>EV Charging</h3>
                {sending &&
                <CircularProgress color="secondary"/>
                }
            </Typography>


            {stations.length > 0 &&
            <div>
                {stations.map(station => (
                    <EvChargingStation
                        station={station}
                        setCmdResult={setCmdResult}
                        setSending={setSending}
                        setStations={setStations}
                        currentSeconds={currentSeconds}
                    />
                ))}
            </div>
            }
            {stations.length === 0 &&
            <h4>Currently no EV charging stations online</h4>
            }


            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
        </Paper>
    </Container>;
};