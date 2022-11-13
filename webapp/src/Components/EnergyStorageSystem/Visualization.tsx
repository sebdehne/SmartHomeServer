import { ESSValues, GridData } from "../../Websocket/types/EnergyStorageSystem";
import { Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@material-ui/core";
import React from "react";
import { eesAlarms, numberTo2Decimal } from "../Utils/utils";


export type VisualizationProps = {
    essValues: ESSValues,
    essState: string,
}
export const Visualization = ({ essValues, essState }: VisualizationProps) => {
    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">Status</TableCell>
                    <TableCell align="right">{essState}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Inverter status</TableCell>
                    <TableCell align="right">{essValues.systemState}</TableCell>
                </TableRow>
                {essValues.inverterAlarms.length > 0 &&
                    <TableRow>
                        <TableCell component="th" scope="row">Inverter Alarms</TableCell>
                        <TableCell align="right">{eesAlarms(essValues.inverterAlarms)}</TableCell>
                    </TableRow>
                }
                {essValues.batteryAlarms.length > 0 &&
                    <TableRow>
                        <TableCell component="th" scope="row">Battery Alarms</TableCell>
                        <TableCell align="right">{eesAlarms(essValues.batteryAlarms)}</TableCell>
                    </TableRow>
                }
                {essValues.batteryAlarm > 0 &&
                    <TableRow>
                        <TableCell component="th" scope="row">Battery Alarm code</TableCell>
                        <TableCell align="right">{essValues.batteryAlarm}</TableCell>
                    </TableRow>
                }
                <TableRow>
                    <TableCell component="th" scope="row">
                        Input
                    </TableCell>
                    <TableCell align="right">{essValues.gridPower} W</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Battery
                    </TableCell>
                    <TableCell align="right">{essValues.batteryPower} W</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Output
                    </TableCell>
                    <TableCell align="right">{essValues.outputPower} W</TableCell>
                </TableRow>

                <TableRow>
                    <TableCell component="th" scope="row"></TableCell>
                    <TableCell align="right"></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Input L1
                    </TableCell>
                    <AcTableCell data={essValues.gridL1}></AcTableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Input L2
                    </TableCell>
                    <AcTableCell data={essValues.gridL2}></AcTableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Input L3
                    </TableCell>
                    <AcTableCell data={essValues.gridL3}></AcTableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row"></TableCell>
                    <TableCell align="right"></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Battery</TableCell>
                    <TableCell align="right">
                        <span style={{ marginRight: "5px" }}>{essValues.soc} % |</span>
                        <span style={{ marginRight: "5px" }}>{numberTo2Decimal(essValues.batteryCurrent)} A |</span>
                        <span>{numberTo2Decimal(essValues.batteryVoltage)} V</span>
                    </TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                    </TableCell>
                    <TableCell align="right"></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Output L1
                    </TableCell>
                    <AcTableCell data={essValues.outputL1}></AcTableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Output L2
                    </TableCell>
                    <AcTableCell data={essValues.outputL2}></AcTableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Output L3
                    </TableCell>
                    <AcTableCell data={essValues.outputL3}></AcTableCell>
                </TableRow>
            </TableBody>
        </Table>
    </TableContainer>

}

type AcTableCellProps = {
    data: GridData
}
const AcTableCell = ({ data }: AcTableCellProps) => {
    return <TableCell align="right">
        <span style={{ marginRight: "5px" }}>{data.power} W | </span>
        <span style={{ marginRight: "5px" }}>{numberTo2Decimal(data.voltage)} V |</span>
        <span>{numberTo2Decimal(data.freq)} Hz</span>
    </TableCell>
}