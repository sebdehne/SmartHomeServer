import Header from "../Header";
import React, { useEffect, useState } from "react";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Checkbox,
    Container,
    Grid,
    TextField
} from "@mui/material";
import { Level, UserRole, UserSettings } from "../../Websocket/types/UserSettings";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import WebsocketClient from "../../Websocket/websocketClient";

export const UserSettingsAdmin = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [users, setUsers] = useState<{ [userId: string]: UserSettings }>({});
    const [newUsername, setNewUsername] = useState<string>('');
    const [deleteConfirmation, setDeleteConfirmation] = useState(false);

    const refresh = () => {
        WebsocketClient.rpc({
            type: "readAllUserSettings"
        }).then(response => setUsers(response.allUserSettings!!))
    };

    useEffect(() => {
        refresh()
    }, []);

    const updateAuth = (user: string, role: UserRole, currentLevel: Level, levelPressed: Level) => {
        let newLevel = levelPressed;
        if (currentLevel === levelPressed) {
            newLevel = "none"
        }
        setSending(true);
        WebsocketClient.rpc({
            type: "writeUserSettings",
            writeUserSettings: {
                user,
                command: "updateAuthorization",
                writeAuthorization: {
                    level: newLevel,
                    userRole: role
                }
            }
        }).then(() => refresh()).finally(() => setSending(false));
    }

    const addUser = () => {
        setSending(true);
        WebsocketClient.rpc({
            type: "writeUserSettings",
            writeUserSettings: {
                user: newUsername.trim(),
                command: "addUser"
            }
        }).then(() => {
                refresh();
                setNewUsername("");
            }
        ).finally(() => setSending(false))
    }

    const deleteUser = (user: string) => {
        setSending(true);
        WebsocketClient.rpc({
            type: "writeUserSettings",
            writeUserSettings: {
                user: user,
                command: "removeUser"
            }
        }).then(() => {
            refresh()
            setDeleteConfirmation(false)
        }).finally(() => setSending(false))
    }

    return <Container maxWidth="sm" className="App">
        <Header
            sending={sending}
            title="User manager"
        />

        {Object.entries(users).map(([userId, userSettings]) => (<Accordion key={userId}>
            <AccordionSummary
                expandIcon={<ExpandMoreIcon/>}
                aria-controls="panel1a-content"
                id="panel1a-header"
            >
                <div style={{ fontWeight: "bold" }}>{userId}</div>
            </AccordionSummary>
            <AccordionDetails>
                <div style={{
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "space-between",
                    width: "100%"
                }}>

                    <div style={{
                        display: "flex",
                        flexDirection: "column",
                        justifyContent: "space-between",
                        width: "100%"
                    }}>
                        {Object.entries(userSettings.authorization).map(([role, level]) => (
                            <div style={{
                                display: "flex",
                                flexDirection: "row",
                                justifyContent: "space-between",
                                width: "100%"
                            }}>
                                <div>{role}:</div>
                                <ButtonGroup variant="contained" style={{
                                    margin: "10px"
                                }}>
                                    <Button
                                        color={level === "read" ? 'secondary' : 'primary'}
                                        onClick={() => updateAuth(userId, role as UserRole, level, "read")}
                                    >Read</Button>
                                    <Button
                                        color={level === "readWrite" ? 'secondary' : 'primary'}
                                        onClick={() => updateAuth(userId, role as UserRole, level, "readWrite")}
                                    >Write</Button>
                                    <Button
                                        color={level === "readWriteAdmin" ? 'secondary' : 'primary'}
                                        onClick={() => updateAuth(userId, role as UserRole, level, "readWriteAdmin")}
                                    >Admin</Button>
                                </ButtonGroup>
                            </div>))}
                    </div>


                    <div style={{
                        display: "flex",
                        flexDirection: "column",
                        justifyContent: "space-between",
                        width: "100%"
                    }}>
                        <div>
                            <Checkbox checked={deleteConfirmation}
                                      onChange={(event, checked) => setDeleteConfirmation(checked)}></Checkbox>
                            Confirmation
                        </div>
                        <Button
                            disabled={!deleteConfirmation}
                            variant="contained"
                            component="span"
                            color={"secondary"}
                            onClick={() => deleteUser(userId)}
                        >Delete user</Button>
                    </div>


                </div>
            </AccordionDetails>
        </Accordion>))}

        <Grid container spacing={2}>
            <div style={{ margin: "10px" }}>&nbsp;</div>
        </Grid>
        <Grid container spacing={2} direction={"row"}>
            <TextField
                value={newUsername}
                label={"User user"}
                placeholder={"example@example.com"}
                onChange={event => setNewUsername(event.target.value.trim())}
                fullWidth
            ></TextField>
            <Button
                variant="contained"
                component="span"
                onClick={() => addUser()}
            >Add new user</Button>
        </Grid>
    </Container>;
}