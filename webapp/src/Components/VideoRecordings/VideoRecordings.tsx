import Header from "../Header";
import React, { useEffect, useMemo, useState } from "react";
import {
    Box,
    Collapse,
    Container,
    IconButton,
    Paper,
    Slider,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography
} from "@material-ui/core";
import { VideoBrowserRequest, VideoBrowserResponse, VideoFile } from "../../Websocket/types/Video";
import websocketClient from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import KeyboardArrowDownIcon from '@material-ui/icons/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@material-ui/icons/KeyboardArrowUp';

const nowDate = () => {
    const fromDate = new Date();
    fromDate.setHours(0)
    fromDate.setMinutes(0);
    fromDate.setSeconds(0);
    fromDate.setMilliseconds(0);
    return fromDate;
};


export const VideoRecordings = () => {

    const [sending, setSending] = useState<boolean>(false);
    const [fileSizeRange, setFileSizeRange] = useState<number[]>([3, 200]);
    const [fileDateRange, setFileDateRange] = useState<number[]>([-2, 0]);

    const [previousRequestSent, setPreviousRequestSent] = useState<VideoBrowserRequest | null>(null);
    const [videoResponse, setVideoResponse] = useState<VideoBrowserResponse | null>(null);

    const dateRange = useMemo(() => {
        const from = fileDateRange[0];
        const to = fileDateRange[1];
        const fromDate = nowDate();
        const toDate = nowDate();
        fromDate.setDate(fromDate.getDate() + from);
        toDate.setDate(toDate.getDate() + to + 1);
        return [fromDate, toDate];
    }, [fileDateRange]);

    useEffect(() => {
        if (previousRequestSent && videoResponse === null) {
            // wait for ongoing rpc to finish first
            return;
        }

        if (previousRequestSent?.fromDate !== dateRange[0].getTime()
            || previousRequestSent?.toDate !== dateRange[1].getTime()
            || previousRequestSent?.minFileSize !== (fileSizeRange[0] * 1000000)
            || previousRequestSent?.maxFileSize !== (fileSizeRange[1] * 1000000)
        ) {

            let request = new VideoBrowserRequest(
                (fileSizeRange[0] * 1000000),
                (fileSizeRange[1] * 1000000),
                dateRange[0].getTime(),
                dateRange[1].getTime()
            );
            setPreviousRequestSent(request);
            setSending(true);
            websocketClient.rpc(new RpcRequest(
                RequestType.videoBrowser,
                null,
                null,
                null,
                null,
                null,
                null,
                request
            ))
                .then(response => setVideoResponse(response.videoBrowserResponse!!))
                .finally(() => setSending(false));
        }

    }, [previousRequestSent, videoResponse, fileSizeRange, fileDateRange, dateRange]);


    return <Container maxWidth="lg" className="App">
        <Header
            title={"Video recordings (" + videoResponse?.dirs.flatMap(d => d.files).length + ")"}
            sending={sending}
        />
        <div style={{
            marginLeft: "50px",
            marginRight: "50px",
        }}>
            <Typography id="non-linear-slider" gutterBottom>
                File size filter: {fileSizeRange[0]} - {fileSizeRange[1]} MB
            </Typography>
            <Slider
                value={fileSizeRange}
                min={0}
                step={1}
                max={200}
                onChange={(e, value) => {
                    setFileSizeRange(value as number[]);
                }}
                valueLabelDisplay="auto"
                aria-labelledby="range-slider"
                getAriaValueText={(value, index) => "test"}
            />
            <Typography id="non-linear-slider" gutterBottom>
                Date filter: {dateRange[0].toLocaleDateString()} - {dateRange[1].toLocaleDateString()}
            </Typography>
            <Slider
                value={fileDateRange}
                min={-30}
                step={1}
                max={0}
                onChange={(e, value) => {
                    setFileDateRange(value as number[]);
                }}
                valueLabelDisplay="auto"
                aria-labelledby="range-slider"
                getAriaValueText={(value, index) => "test"}
            />
        </div>

        <TableContainer component={Paper} style={{
            marginTop: "20px"
        }}>
            <Table aria-label="simple table">
                <TableHead>
                    <TableRow>
                        <TableCell/>
                        <TableCell>Date/Time</TableCell>
                        <TableCell>Size</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {videoResponse?.dirs.flatMap(d => d.files.map(f => ({ dirname: d.dirname, file: f })))
                        .sort((a, b) => b.file.timestampMillis - a.file.timestampMillis)
                        .map((file) => (
                            <Row key={file.file.timestampMillis} file={file.file} dirname={file.dirname}/>
                        ))}
                </TableBody>
            </Table>
        </TableContainer>
    </Container>;
};

type RowProps = {
    dirname: string,
    file: VideoFile
}
const Row = ({ file, dirname }: RowProps) => {
    const [open, setOpen] = React.useState(false);
    return <>
        <TableRow>
            <TableCell>
                <IconButton aria-label="expand row" size="small" onClick={() => setOpen(!open)}>
                    {open ? <KeyboardArrowUpIcon/> : <KeyboardArrowDownIcon/>}
                </IconButton>
            </TableCell>
            <TableCell component="th" scope="row">
                {formatDate(new Date(file.timestampMillis))}
            </TableCell>
            <TableCell component="th" scope="row">            {humanFileSize(file.size)}        </TableCell>
        </TableRow>
        <TableRow>
            <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={6}>
                <Collapse in={open} timeout="auto" unmountOnExit>
                    <Box margin={1}>
                        <video controls autoPlay={true}>
                            <source src={"video/" + dirname + "/" + file.filename} type="video/mp4"/>
                        </video>
                    </Box>
                </Collapse>
            </TableCell>
        </TableRow>
    </>;
};

const formatDate = (date: Date) =>
    date.getFullYear()
    + "-" + (date.getMonth() + 1).toString().padStart(2, '0')
    + "-" + date.getDate().toString().padStart(2, '0')
    + " " + date.getHours().toString().padStart(2, '0')
    + ":" + date.getMinutes().toString().padStart(2, '0')
    + ":" + date.getSeconds().toString().padStart(2, '0');



function humanFileSize(bytes: number, si = false, dp = 1) {
    const thresh = si ? 1000 : 1024;

    if (Math.abs(bytes) < thresh) {
        return bytes + ' B';
    }

    const units = si
        ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
        : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    const r = 10 ** dp;

    do {
        bytes /= thresh;
        ++u;
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);


    return bytes.toFixed(dp) + ' ' + units[u];
}