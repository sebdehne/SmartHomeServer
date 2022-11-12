import { Accordion, AccordionDetails, AccordionSummary, Button, Container, Grid, TextField } from "@material-ui/core";
import React, { useEffect, useState } from "react";
import Header from "../Header";
import {
    EnergyPriceConfig,
    EnergyPricingSettingsRead,
    EnergyPricingSettingsWrite
} from "../../Websocket/types/EnergyPricingSettings";
import WebsocketService from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import { numberNok } from "../Utils/utils";


export const EnergyPricingSettings = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [settings, setSettings] = useState<EnergyPricingSettingsRead>();
    const [threshold, setThreshold] = useState("");

    const reload = () => {
        setSending(true)
        WebsocketService.rpc({
            type: "readEnergyPricingSettings"
        })
            .then(resp => setSettings(resp.energyPricingSettingsRead))
            .finally(() => setSending(false))
    };

    const saveAndReload = (energyPricingSettingsWrite: EnergyPricingSettingsWrite) => {
        setSending(true)
        WebsocketService.rpc({
            type: "writeEnergyPricingSettings",
            energyPricingSettingsWrite
        })
            .then(resp => setSettings(resp.energyPricingSettingsRead))
            .finally(() => setSending(false))
    }

    useEffect(() => {
        reload();
    }, []);

    return <Container maxWidth="sm" className="App">
        <Header
            title={"Skip expensive hours %"}
            sending={sending}
        />

        {settings &&
            <Accordion key={"dummy"}>
                <AccordionSummary
                    expandIcon={<ExpandMoreIcon/>}
                    aria-controls="panel1a-content"
                    id="panel1a-header"
                >
                    <div
                        style={{
                            width: "100%",
                            display: "flex",
                            flexDirection: "row",
                            justifyContent: "space-between"
                        }}
                    >
                        <div>Always OK under:</div>
                        <div>{numberNok(settings!!.pricingThreshold)}</div>
                    </div>
                </AccordionSummary>
                <AccordionDetails>
                    <Grid container spacing={2}>
                        <Grid item xs={6}/>
                        <Grid item xs={3}>
                            <TextField
                                value={threshold}
                                onChange={event => {
                                    let newValue = event.target.value.trim();
                                    newValue = newValue.replaceAll(",", ".")
                                    setThreshold(newValue);
                                }
                                }
                            />
                        </Grid>
                        <Grid item xs={3}>
                            <Button onClick={() => {
                                saveAndReload({
                                    pricingThreshold: parseFloat(threshold)
                                })
                            }
                            }>Update</Button>
                        </Grid>
                    </Grid>
                </AccordionDetails>
            </Accordion>
        }
        {settings && settings.serviceToSkipPercentExpensiveHours.length > 0 &&
            <div>
                {settings.serviceToSkipPercentExpensiveHours.map(config => <ServiceToSkipPercentExpensiveHours
                    key={config.service}
                    config={config}
                    saveAndReload={saveAndReload}
                />)}
            </div>
        }

    </Container>
};

type ServiceToSkipPercentExpensiveHoursProps = {
    config: EnergyPriceConfig;
    saveAndReload: (energyPricingSettingsWrite: EnergyPricingSettingsWrite) => void
}

const ServiceToSkipPercentExpensiveHours = ({
                                                config,
                                                saveAndReload,
                                            }: ServiceToSkipPercentExpensiveHoursProps) => {

    const [text, setText] = useState("");

    return <Accordion>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
            aria-controls="panel1a-content"
            id="panel1a-header"
        >
            <div
                style={{
                    width: "100%",
                    display: "flex",
                    flexDirection: "row",
                    justifyContent: "space-between"
                }}
            >
                <div>{config.service}</div>
                {config.mustWaitUntil && <div>{config.mustWaitUntil}</div>}
                <div>{config.skipPercentExpensiveHours}%</div>
            </div>
        </AccordionSummary>
        <AccordionDetails>
            <Grid container spacing={2}>
                <Grid item xs={6}/>
                <Grid item xs={3}>
                    <TextField
                        value={text}
                        onChange={event => {
                            let newValue = event.target.value.trim();
                            setText(parseInt(newValue).toString())
                        }
                        }
                    />
                </Grid>
                <Grid item xs={3}>
                    <Button onClick={() => {
                        saveAndReload({
                            serviceToSkipPercentExpensiveHours: {
                                [config.service]: parseInt(text)
                            }
                        })
                    }
                    }>Update</Button>
                </Grid>
            </Grid>
        </AccordionDetails>
    </Accordion>;
}
