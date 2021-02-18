import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CircularProgress, Container, Paper, Typography } from "@material-ui/core";
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
import Checkbox from '@material-ui/core/Checkbox';
import Select from '@material-ui/core/Select';
import MenuItem from '@material-ui/core/MenuItem';

interface SelectedClientByFirmware {
    [firmware: string]: number | null
}

const EVChargingStationsManager = () => {
    let history = useHistory();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);

    const [connectedClients, setConnectedClients] = useState<EvChargingStationClient[]>([]);
    const [firmwareVersions, setFirmwareVersions] = useState<string[]>([]);
    const [selectedClientByFirmware, setSelectedClientByFirmware] = useState<SelectedClientByFirmware>({});

    const onSelectClientForFirmware = (firmware: string, clientId: number | null) => {
        setSelectedClientByFirmware(prevState => ({
            ...prevState,
            [firmware]: clientId
        }));
    };

    const requestFirmwareUpload = (firmware: string, clientId: number) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.evChargingStationRequest,
            null,
            null,
            null,
            null,
            new EvChargingStationRequest(
                EvChargingStationRequestType.uploadFirmwareToClient,
                clientId,
                firmware
            )
        )).then(response => {
            setCmdResult(response.evChargingStationResponse!!.uploadFirmwareToClientResult);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
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
                return WebsocketService.rpc(new RpcRequest(
                    RequestType.evChargingStationRequest,
                    null,
                    null,
                    null,
                    null,
                    new EvChargingStationRequest(
                        EvChargingStationRequestType.listAllFirmwareVersions,
                        null,
                        null
                    )
                ))
            }).then(response => {
                setFirmwareVersions(response.evChargingStationResponse!!.allFirmwareVersions!!);
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
                    <h4>Connected EV charing stations:</h4>
                    <div>
                        {connectedClients.map(client => (
                            <div key={client.addr}>
                                <Checkbox/> <ClientComponent client={client}/>
                            </div>
                        ))}
                    </div>
                </div>
                }
                {connectedClients.length === 0 &&
                <h4>Currently no EV charing stations online</h4>
                }

                {firmwareVersions.length > 0 &&
                <div>
                    {firmwareVersions.map(firmwareVersion => (
                        <Card key={firmwareVersion}>
                            <CardContent>
                                <Typography color="textSecondary">
                                    {firmwareVersion}
                                </Typography>

                                Upload to client: <ClientSelect
                                value={selectedClientByFirmware[firmwareVersion]}
                                connectedClients={connectedClients}
                                setValue={clientId => onSelectClientForFirmware(firmwareVersion, clientId)}
                            /> <Button
                                disabled={selectedClientByFirmware[firmwareVersion] === null}
                                onClick={() => requestFirmwareUpload(firmwareVersion, selectedClientByFirmware[firmwareVersion]!!)}>Upload</Button>
                            </CardContent>
                        </Card>
                    ))}
                </div>

                }


                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            </Paper>
        </Container>
    );
};

export default EVChargingStationsManager;

type ClientSelectProps = {
    connectedClients: EvChargingStationClient[],
    value: number | null,
    setValue: (clientId: number | null) => void
}
const ClientSelect = ({ connectedClients, value, setValue }: ClientSelectProps) => {
    return (
        <Select
            labelId="demo-simple-select-label"
            id="demo-simple-select"
            value={value}
            onChange={e => setValue(e.target.value as number)}
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
                Client {props.client.clientId}
            </span>
            <span> (currentVersion={props.client.firmwareVersion}, addr={props.client.addr}:{props.client.port})</span>
        </>
    )
};
