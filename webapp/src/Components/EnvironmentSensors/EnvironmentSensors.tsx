import React, { useEffect, useState } from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { Container } from "@material-ui/core";
import Header from "../Header";
import {
    EnvironmentSensorRequest,
    EnvironmentSensorRequestType,
    EnvironmentSensorState,
    FirmwareInfo
} from "../../Websocket/types/EnvironmentSensors";
import { EnvironmentSensor, getSensorStatus, SensorStatus } from "./EnvironmentSensor";
import { FirmwareUpload } from "./FirmwareUpload";

export const EnvironmentSensors = () => {

    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [sensors, setSensors] = useState<EnvironmentSensorState[]>([]);
    const [firmwareInfo, setFirmwareInfo] = useState<FirmwareInfo | null>(null);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds]);

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.environmentSensorEvents, notify => {
                setSensors(notify.environmentSensorEvent!!.sensors);
                setFirmwareInfo(notify.environmentSensorEvent!!.firmwareInfo);
            },
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
            )).then(response => {
                setSensors(response.environmentSensorResponse!!.sensors);
                setFirmwareInfo(response.environmentSensorResponse!!.firmwareInfo);
            }));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const byFirmwareVersions: { [key: number]: number; } = {};
    sensors.forEach(s => {
        byFirmwareVersions[s.firmwareVersion] = (byFirmwareVersions[s.firmwareVersion] || 0) + 1;
    });

    return <Container maxWidth="sm" className="App">
        <Header
            title={"Environment Sensors (" + sensors.filter(s => getSensorStatus(s, currentSeconds) === SensorStatus.green).length + "/" + sensors.length + ")"}
            sending={sending}
        />

        {
            Object.keys(byFirmwareVersions).map(v => parseInt(v)).sort().map(version => (
                <div>
                    {Object.keys(byFirmwareVersions).length > 1 &&
                    <h4>Firmware version: {version}</h4>
                    }
                    {sensors
                        .filter(s => s.firmwareVersion === version)
                        .sort((a, b) => {
                            return a.displayName.localeCompare(b.displayName);
                        })
                        .map(sensor => (
                            <EnvironmentSensor
                                key={sensor.sensorId}
                                sensor={sensor}
                                setCmdResult={setCmdResult}
                                setSending={setSending}
                                setSensors={setSensors}
                                currentMilliSeconds={currentSeconds}
                                firmwareInfo={firmwareInfo}
                            />
                        ))}
                </div>
            ))
        }

        {sensors.length === 0 &&
        <h4>Currently no Environment Sensor online</h4>
        }

        <FirmwareUpload
            setSending={setSending}
            setCmdResult={setCmdResult}
            setFirmwareInfo={setFirmwareInfo}
            firmwareInfo={firmwareInfo}
        />

        {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
        {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
    </Container>;
};