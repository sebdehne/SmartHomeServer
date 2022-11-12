import React, { useEffect, useState } from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { Container } from "@material-ui/core";
import Header from "../Header";
import {
    EnvironmentSensorRequest,
    EnvironmentSensorState,
    FirmwareInfo
} from "../../Websocket/types/EnvironmentSensors";
import { EnvironmentSensor, getSensorStatus, SensorStatus } from "./EnvironmentSensor";
import { AdminTools } from "./AdminTools";

export const EnvironmentSensors = () => {

    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [sensors, setSensors] = useState<EnvironmentSensorState[]>([]);
    const [firmwareInfo, setFirmwareInfo] = useState<FirmwareInfo | undefined>(undefined);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds]);

    useEffect(() => {
        const subId = WebsocketService.subscribe("environmentSensorEvents", notify => {
                setSensors(notify.environmentSensorEvent!!.sensors);
                setFirmwareInfo(notify.environmentSensorEvent!!.firmwareInfo!!);
            },
            () => WebsocketService.rpc({
                type: "environmentSensorRequest",
                environmentSensorRequest: { type: "getAllEnvironmentSensorData" }
            }).then(response => {
                setSensors(response.environmentSensorResponse!!.sensors);
                setFirmwareInfo(response.environmentSensorResponse!!.firmwareInfo);
            }));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const byFirmwareVersions: { [key: number]: number; } = {};
    sensors.forEach(s => {
        byFirmwareVersions[s.firmwareVersion] = (byFirmwareVersions[s.firmwareVersion] || 0) + 1;
    });

    const sendUpdate = (req: EnvironmentSensorRequest) => {
        setSending(true);
        WebsocketService.rpc({ type: "environmentSensorRequest", environmentSensorRequest: req })
            .then(response => {
                setCmdResult(true);
                setSensors(response.environmentSensorResponse!!.sensors);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            })
            .finally(() => setSending(false));
    };

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
                                currentMilliSeconds={currentSeconds}
                                firmwareInfo={firmwareInfo}
                                sendUpdate={sendUpdate}
                            />
                        ))}
                </div>
            ))
        }

        {sensors.length === 0 &&
            <h4>Currently no Environment Sensor online</h4>
        }

        <AdminTools
            setSending={setSending}
            setCmdResult={setCmdResult}
            setFirmwareInfo={setFirmwareInfo}
            firmwareInfo={firmwareInfo}
            sendUpdate={sendUpdate}
        />

        {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
        {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
    </Container>;
};