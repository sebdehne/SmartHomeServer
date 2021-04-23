import React, { useEffect, useState } from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { Container } from "@material-ui/core";
import Header from "../Header";
import {
    EnvironmentSensorRequest,
    EnvironmentSensorRequestType,
    EnvironmentSensorState
} from "../../Websocket/types/EnvironmentSensors";
import { EnvironmentSensor } from "./EnvironmentSensor";
import { FirmwareUpload } from "./FirmwareUpload";

export const EnvironmentSensors = () => {

    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [sensors, setSensors] = useState<EnvironmentSensorState[]>([]);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds]);

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.environmentSensorEvents, notify =>
                setSensors(notify.environmentSensorEvent!!.sensors),
            () => WebsocketService.rpc(new RpcRequest(
                RequestType.environmentSensorRequest,
                null,
                null,
                null,
                null,
                null,
                new EnvironmentSensorRequest(
                    EnvironmentSensorRequestType.getAllEnvironmentSensorData,
                    null,
                    null,
                    null,
                    null
                )
            )).then(response => setSensors(response.environmentSensorResponse!!.sensors)));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return <Container maxWidth="sm" className="App">
        <Header
            title="Environment Sensors"
            sending={sending}
        />

        {sensors.length > 0 &&
        <div>
            {sensors
                .sort((a, b) => {
                    return a.sensorId - b.sensorId;
                })
                .map(sensor => (
                <EnvironmentSensor
                    key={sensor.sensorId}
                    sensor={sensor}
                    setCmdResult={setCmdResult}
                    setSending={setSending}
                    setSensors={setSensors}
                    currentMilliSeconds={currentSeconds}
                />
            ))}
        </div>
        }
        {sensors.length === 0 &&
        <h4>Currently no Environment Sensor online</h4>
        }

        <FirmwareUpload
            setSending={setSending}
            setCmdResult={setCmdResult}/>

        {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
        {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
    </Container>;
};