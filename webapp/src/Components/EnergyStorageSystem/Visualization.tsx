import { ESSValues, GridData } from "../../Websocket/types/EnergyStorageSystem";
import { Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@material-ui/core";
import React from "react";


export type VisualizationProps = {
    essValues: ESSValues
}
export const Visualization = ({ essValues }: VisualizationProps) => {
    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">Status</TableCell>
                    <TableCell align="right">{essValues.systemState}</TableCell>
                </TableRow>

                <TableRow>
                    <TableCell component="th" scope="row">
                    </TableCell>
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
                    <TableCell component="th" scope="row">
                        Ac Input Total
                    </TableCell>
                    <TableCell align="right">{essValues.gridPower} W</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                    </TableCell>
                    <TableCell align="right"></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Battery power</TableCell>
                    <TableCell align="right">{essValues.batteryPower} W</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Battery SoC</TableCell>
                    <TableCell align="right">{essValues.soc} %</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Battery current</TableCell>
                    <TableCell align="right">{new Intl.NumberFormat('nb-NO', { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(essValues.batteryCurrent)} A</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Battery current</TableCell>
                    <TableCell align="right">{new Intl.NumberFormat('nb-NO', { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(essValues.batteryVoltage)} V</TableCell>
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
                <TableRow>
                    <TableCell component="th" scope="row">
                        Ac Output Total
                    </TableCell>
                    <TableCell align="right">{essValues.outputPower} W</TableCell>
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
        <span style={{marginRight: "5px"}}>{data.power} W | </span>
        <span style={{marginRight: "5px"}}>{new Intl.NumberFormat('nb-NO', { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(data.voltage)} V |</span>
        <span>{new Intl.NumberFormat('nb-NO', { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(data.freq)} Hz</span>
    </TableCell>
}