import { Accordion, AccordionDetails, AccordionSummary, Button, Container, Grid, TextField } from "@material-ui/core";
import React, { useEffect, useState } from "react";
import Header from "../Header";
import {
    CategorizedPrice,
    EnergyPriceConfig,
    EnergyPricingSettingsWrite,
    PriceCategory
} from "../../Websocket/types/EnergyPricingSettings";
import WebsocketService from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import { formatTime } from "../Utils/dateUtils";
import { Bar } from 'react-chartjs-2';
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Title, Tooltip, } from 'chart.js';
import { ArrowLeft, ArrowRight } from "@material-ui/icons";

ChartJS.register(
    CategoryScale,
    LinearScale,
    BarElement,
    Title,
    Tooltip,
    Legend
);


export const EnergyPricingSettings = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [settings, setSettings] = useState<EnergyPriceConfig[]>([]);
    const [textfields, setTextfields] = useState<any>({});
    const [dayOffset, setDayOffset] = useState<number>(0);

    const reload = () => {
        setSending(true)
        WebsocketService.rpc({
            type: "readEnergyPricingSettings"
        })
            .then(resp => setSettings(resp.energyPricingSettingsRead!!.serviceConfigs))
            .finally(() => setSending(false))
    };

    const saveAndReload = (energyPricingSettingsWrite: EnergyPricingSettingsWrite) => {
        setSending(true)
        WebsocketService.rpc({
            type: "writeEnergyPricingSettings",
            energyPricingSettingsWrite
        })
            .then(resp => setSettings(resp.energyPricingSettingsRead!!.serviceConfigs))
            .finally(() => setSending(false))
    }

    useEffect(() => reload(), []);

    let selectedDay = new Date();
    selectedDay.setDate(selectedDay.getDate() + dayOffset);

    return <Container maxWidth="sm" className="App">
        <Header
            title={"Enery pricing config"}
            sending={sending}
        />

        <div>
            <Button
                disabled={dayOffset < 0}
                style={{ margin: "10px" }} variant="contained" color="primary"
                onClick={() => setDayOffset(dayOffset - 1)}>
                <ArrowLeft/>
            </Button>
            <span>{selectedDay.toLocaleDateString()}</span>
            <Button
                disabled={dayOffset > 0}
                style={{ margin: "10px" }} variant="contained" color="primary"
                onClick={() => setDayOffset(dayOffset + 1)}>
                <ArrowRight/>
            </Button>
        </div>

        {settings.map(s => <>
            <Accordion key={s.service}>
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
                        <div>{s.service}</div>
                        {s.priceDecision &&
                            <div>{s.priceDecision.current} until {formatTime(s.priceDecision.changesAt)}</div>}
                        {!s.priceDecision && <div></div>}
                    </div>
                </AccordionSummary>
                <AccordionDetails>
                    <Grid container spacing={2} direction={"column"}>
                        <Grid container spacing={2} direction={"row"}>
                            <Grid item xs={12}>
                                <Bar data={
                                    {
                                        labels: s.categorizedPrices.filter(dayFilter(selectedDay)).map(toHours),
                                        datasets: [
                                            {
                                                label: "cheap",
                                                data: s.categorizedPrices.filter(dayFilter(selectedDay)).map(toPrice("cheap")),
                                                borderColor: 'rgb(96,255,53)',
                                                backgroundColor: 'rgba(96,255,53, 0.5)',
                                            },
                                            {
                                                label: "neutral",
                                                data: s.categorizedPrices.filter(dayFilter(selectedDay)).map(toPrice("neutral")),
                                                borderColor: 'rgb(99,125,255)',
                                                backgroundColor: 'rgba(99,125,255, 0.5)',
                                            },
                                            {
                                                label: "expensive",
                                                data: s.categorizedPrices.filter(dayFilter(selectedDay)).map(toPrice("expensive")),
                                                borderColor: 'rgb(255, 99, 132)',
                                                backgroundColor: 'rgba(255, 99, 132, 0.5)',
                                            },

                                        ]
                                    }
                                } options={{
                                    responsive: true
                                }}/>
                            </Grid>
                        </Grid>
                        <Grid container spacing={2} direction={"row"}>
                            <Grid item xs={3}>
                                <TextField
                                    placeholder={"neutralSpan"}
                                    value={textfields["neutralSpan." + s.service] || ""}
                                    onChange={event => {
                                        let newValue = event.target.value.trim();
                                        setTextfields(({
                                            ...textfields,
                                            ["neutralSpan." + s.service]: newValue
                                        }))
                                    }}
                                />
                            </Grid>
                            <Grid item xs={3}>
                                <TextField
                                    placeholder={"avgMultiplier"}
                                    value={textfields["avgMultiplier." + s.service] || ""}
                                    onChange={event => {
                                        let newValue = event.target.value.trim();
                                        setTextfields(({
                                            ...textfields,
                                            ["avgMultiplier." + s.service]: newValue
                                        }))
                                    }}
                                />
                            </Grid>
                            <Grid item xs={3}>
                                <Button onClick={() => {
                                    let req: EnergyPricingSettingsWrite = {
                                        service: s.service
                                    }
                                    const np = textfields["neutralSpan." + s.service];
                                    if (np && !isNaN(parseFloat(np))) {
                                        req = {
                                            ...req,
                                            neutralSpan: parseFloat(np)
                                        }
                                    }

                                    const am = textfields["avgMultiplier." + s.service];
                                    if (am && !isNaN(parseFloat(am))) {
                                        req = {
                                            ...req,
                                            avgMultiplier: parseFloat(am)
                                        }
                                    }

                                    saveAndReload(req)
                                }
                                }>Update</Button>
                            </Grid>
                        </Grid>
                    </Grid>

                </AccordionDetails>
            </Accordion>
        </>)}

    </Container>
};

const dayFilter = (now: Date) => (p: CategorizedPrice): boolean => {
    return new Date(p.price.from).toLocaleDateString() === now.toLocaleDateString()
}

const toHours = (p: CategorizedPrice): string => {
    const d = new Date(p.price.from);
    let h = d.getHours();
    let hStr = "";
    if (h < 10) {
        hStr = "0" + h;
    } else {
        hStr = h.toString();
    }

    return hStr;
}

const toPrice = (c: PriceCategory) => (p: CategorizedPrice): number | null => {
    if (p.category === c) {
        return p.price.price;
    }
    return null;
}