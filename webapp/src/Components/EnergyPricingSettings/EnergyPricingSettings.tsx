import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    Container,
    Grid,
    MenuItem,
    Paper,
    Select, SelectChangeEvent,
    TextField
} from "@mui/material";
import React, { Dispatch, SetStateAction, useEffect, useState } from "react";
import Header from "../Header";
import {
    CategorizedPrice,
    EnergyConsumptionData,
    EnergyPriceConfig,
    EnergyPricingSettingsWrite,
    PriceCategory
} from "../../Websocket/types/EnergyPricingSettings";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { currentDay, formatTime } from "../Utils/dateUtils";
import { Bar, Doughnut } from 'react-chartjs-2';
import {
    ArcElement,
    BarElement,
    CategoryScale,
    Chart as ChartJS,
    ChartOptions,
    Legend,
    LinearScale,
    Title,
    Tooltip,
} from 'chart.js';
import { ArrowLeft, ArrowRight } from "@mui/icons-material";
import { isNumber, numberTo2Decimal } from "../Utils/utils";

ChartJS.register(
    CategoryScale,
    LinearScale,
    BarElement,
    Title,
    Tooltip,
    Legend,
    ArcElement
);


export const EnergyPricingSettings = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [settings, setSettings] = useState<EnergyPriceConfig[]>([]);
    const [textfields, setTextfields] = useState<any>({});
    const [dayOffset, setDayOffset] = useState<number>(0);
    const userSettings = useUserSettings();

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
                            <Grid size={12}>
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
                            <Grid size={3}>
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
                            <Grid size={3}>
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
                            <Grid size={3}>
                                <Button
                                    disabled={!userSettings.userCanWrite("energyPricing")}
                                    onClick={() => {
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

        <div style={{ margin: "10px" }}/>

        <EnergyConsumption setSending={setSending}/>
    </Container>
};

const dayFilter = (now: Date) => (p: CategorizedPrice): boolean => {
    return new Date(p.price.from).toLocaleDateString() === now.toLocaleDateString()
}

const toHours = (p: CategorizedPrice): string => {
    const d = new Date(p.price.from);
    let h = d.getHours();
    let hStr: string;
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


type EnergyConsumptionProps = {
    setSending: Dispatch<SetStateAction<boolean>>;

}

const EnergyConsumption = ({ setSending }: EnergyConsumptionProps) => {
    const [selectedPeriode, setSelectedPeriode] = useState<string>("day");
    const [extraParam, setExtraParam] = useState<string>(currentDay().toString());
    const [data, setData] = useState<EnergyConsumptionData>();

    const handleChange = (event: SelectChangeEvent) => {
        setSelectedPeriode(event.target.value as string);
    };

    useEffect(() => {
        let start = ""
        let stop = "";
        if (selectedPeriode === "month" && isNumber(extraParam)) {
            let d = new Date();
            d.setHours(0);
            d.setMinutes(0);
            d.setSeconds(0);
            d.setMilliseconds(0);
            d.setDate(1);
            d.setMonth(parseInt(extraParam.trim()) - 1);
            start = d.toISOString();
            d.setMonth(d.getMonth() + 1);
            stop = d.toISOString();
        } else {
            let d = new Date();
            d.setHours(0);
            d.setMinutes(0);
            d.setSeconds(0);
            d.setMilliseconds(0);
            if (isNumber(extraParam)) {
                d.setDate(parseInt(extraParam.trim()));
            } else {
            }
            start = d.toISOString();
            d.setDate(d.getDate() + 1);
            stop = d.toISOString();
        }

        setSending(true);
        WebsocketService.rpc({
            type: "energyConsumptionQuery",
            energyConsumptionQuery: {
                start,
                stop
            }
        })
            .then(resp => setData(resp.energyConsumptionData))
            .finally(() => setSending(false));

    }, [extraParam, selectedPeriode, setSending]);

    function mapData(data: EnergyConsumptionData) {
        return ({
            labels: Object.keys(data.houseKnownConsumers),
            datasets: [{
                label: "Energy consumption",
                data: Object.keys(data.houseKnownConsumers).map(key => data.houseKnownConsumers[key] / 1000),
                backgroundColor: [
                    'rgba(255, 99, 132, 0.2)',
                    'rgba(54, 162, 235, 0.2)',
                    'rgba(255, 206, 86, 0.2)',
                    'rgba(75, 192, 192, 0.2)',
                    'rgba(153, 102, 255, 0.2)',
                    'rgba(255, 159, 64, 0.2)',
                ],
                borderColor: [
                    'rgba(255, 99, 132, 1)',
                    'rgba(54, 162, 235, 1)',
                    'rgba(255, 206, 86, 1)',
                    'rgba(75, 192, 192, 1)',
                    'rgba(153, 102, 255, 1)',
                    'rgba(255, 159, 64, 1)',
                ],
                borderWidth: 1,
            }],
        })
    }


    let options: ChartOptions<"doughnut"> = {
        plugins: {
            legend: {
                labels: {
                    generateLabels: chart => {
                        const datasets = chart.data.datasets;
                        const labels = chart.data.labels as string[];
                        let dataset = datasets[0];
                        return dataset.data.map((data, i) => {
                            // @ts-ignore
                            let backgroundColorElement = dataset.backgroundColor[i];
                            return ({
                                text: `${labels[i]}: ${numberTo2Decimal(data as number)} kWh`,
                                fillStyle: backgroundColorElement,
                                fontColor: 'rgb(255,255,255)'
                            });
                        })
                    }
                }
            }
        }
    };
    return <Paper>
        <h4>Energy Consumption</h4>
        <div style={{ display: "flex", flexDirection: "column" }}>
            <div style={{ display: "flex", flexDirection: "row" }}>
                <Select
                    value={selectedPeriode}
                    onChange={handleChange}
                >
                    <MenuItem value={"day"}>Day</MenuItem>
                    <MenuItem value={"month"}>Month</MenuItem>
                </Select>
                <TextField value={extraParam} onChange={event => setExtraParam(event.target.value)}></TextField>
            </div>
            {data && <div style={{ display: "flex", flexDirection: "row" }}>
                <div>Grid: {numberTo2Decimal(data.grid / 1000)} kWh</div>
                <div style={{ display: "flex", flexDirection: "column" }}>
                    <div>Victron</div>
                    <div>|</div>
                    <div>Battery: {numberTo2Decimal(data.battery / 1000)} kWh</div>
                </div>
                <div style={{ display: "flex", flexDirection: "column" }}>
                    <div>House: {numberTo2Decimal(data.houseTotal / 1000)} kWh</div>
                    <div>
                        <Doughnut data={mapData(data)} options={options}/>
                    </div>
                </div>

            </div>}
        </div>
    </Paper>
}


