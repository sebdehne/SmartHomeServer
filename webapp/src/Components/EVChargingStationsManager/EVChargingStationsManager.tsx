import React, { useEffect, useState } from 'react';
import { Button, CircularProgress, Container, Paper, Typography } from "@material-ui/core";
import { useHistory } from "react-router-dom";
import WebsocketService from "../../Websocket/websocketClient";
import ConnectionStatusComponent from "../ConnectionStatus";
import {
    EvChargingStationClient,
    EvChargingStationRequest,
    EvChargingStationRequestType
} from "../../Websocket/types/EVChargingStation";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import Select from '@material-ui/core/Select';
import MenuItem from '@material-ui/core/MenuItem';


function arrayBufferToBase64(buffer: ArrayBuffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
}

const EVChargingStationsManager = () => {
    let history = useHistory();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);

    const [connectedClients, setConnectedClients] = useState<EvChargingStationClient[]>([]);
    const [selectedClientForFirmwareUpload, setSelectedClientForFirmwareUpload] = useState<string | null>(null);

    const uploadFirmware = (clientId: string, file: File) => {
        setSelectedClientForFirmwareUpload(null);
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc(new RpcRequest(
                RequestType.evChargingStationRequest,
                null,
                null,
                null,
                null,
                new EvChargingStationRequest(
                    EvChargingStationRequestType.uploadFirmwareToClient,
                    clientId,
                    firmwareBased64Encoded
                )
            )).then(response => {
                setCmdResult(response.evChargingStationResponse!!.uploadFirmwareToClientResult);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            }).finally(() => setSending(false));

        };
        reader.onerror = ev => {
            console.log("Error reading file:");
            console.log(ev);
        };
        reader.readAsArrayBuffer(file);
    };

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.evChargingStationConnections, notify => {
            setConnectedClients(notify.evChargingStationEvent!!.connectedClients!!);
        }, () => {
            WebsocketService.rpc(new RpcRequest(
                RequestType.evChargingStationRequest,
                null,
                null,
                null,
                null,
                new EvChargingStationRequest(
                    EvChargingStationRequestType.getConnectedClients,
                    null,
                    null
                )
            )).then(response => {
                setConnectedClients(response.evChargingStationResponse!!.connectedClients!!);
            });
        });

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <ConnectionStatusComponent/>
                <Button variant="text" color="secondary" onClick={() => {
                    history.push("/")
                }}>
                    Home
                </Button>

                <Typography>
                    <h2 style={{ display: "inline" }}>EV Charging station manager</h2>
                    {sending &&
                    <CircularProgress color="secondary"/>
                    }
                </Typography>

                {connectedClients.length > 0 &&
                <div>
                    <h4>Connected EV charging stations:</h4>
                    <ul>
                        {connectedClients.map(client => (
                            <li key={client.addr}>
                                <ClientComponent client={client}/>
                            </li>
                        ))}
                    </ul>
                </div>
                }
                {connectedClients.length === 0 &&
                <h4>Currently no EV charging stations online</h4>
                }

                <div>
                    <h4>Firmware uploader</h4>
                    <ClientSelect
                        connectedClients={connectedClients}
                        value={selectedClientForFirmwareUpload}
                        setValue={clientId => setSelectedClientForFirmwareUpload(clientId)}
                    />
                    {selectedClientForFirmwareUpload &&
                    <div>
                        <input
                            accept=".bin"
                            hidden
                            id="firmwareUploadFileSelector"
                            type="file"
                            onChange={e => {
                                uploadFirmware(selectedClientForFirmwareUpload!!, e.target.files!![0]);
                            }}
                        />
                        <label htmlFor="firmwareUploadFileSelector">
                            <Button variant="text" component="span">
                                Upload firmware
                            </Button>
                        </label>
                    </div>
                    }

                </div>

                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            </Paper>
        </Container>
    );
};

export default EVChargingStationsManager;

type ClientSelectProps = {
    connectedClients: EvChargingStationClient[],
    value: string | null,
    setValue: (clientId: string | null) => void
}
const ClientSelect = ({ connectedClients, value, setValue }: ClientSelectProps) => {
    return (
        <Select
            labelId="demo-simple-select-label"
            id="demo-simple-select"
            value={value}
            onChange={e => setValue(e.target.value as string)}
        >
            <MenuItem value={undefined}/>
            {connectedClients.map(c => (
                <MenuItem value={c.clientId}><ClientComponent client={c}/></MenuItem>
            ))}
        </Select>
    );
};

type ClientComponentProps = {
    client: EvChargingStationClient
}
const ClientComponent = (props: ClientComponentProps) => {
    return (
        <>
            <span>
                {props.client.clientId}
            </span>
            <span> (currentVersion={props.client.firmwareVersion}, addr={props.client.addr}:{props.client.port})</span>
        </>
    )
};
