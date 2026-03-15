import {FirewallElement, ServiceState} from "../../Websocket/types/Firewall";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Checkbox,
    IconButton,
    TextField
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import React, {useCallback, useState} from "react";
import {isNumber} from "../Utils/utils";
import WebsocketClient from "../../Websocket/websocketClient";
import DeleteIcon from "@mui/icons-material/Delete";

export type FirewallServiceProps = {
    name: string;
    state: ServiceState;
}
export const FirewallService = ({
                                    name, state
                                }: FirewallServiceProps) => {

    const [editState, setEditState] = useState<ServiceState | undefined>();

    const editElement = useCallback((element: FirewallElement, path: string[]) => {
        setEditState(prev => {

            if (!prev) return undefined;
            let root = JSON.parse(JSON.stringify(prev)) as ServiceState;
            let current = root.elements;

            function edit(path: string[]) {
                const [head, ...tail] = path;
                let index = isNumber(head) ? parseInt(head) : current.findIndex((e) => e.name === head);

                if (tail.length === 0) {
                    current[index] = element;
                } else {
                    current = current[index].value as FirewallElement[];
                    edit(tail);
                }
            }

            edit(path);
            return root;
        })
    }, []);

    const submit = useCallback(() => {
        if (editState) {
            WebsocketClient.rpc({
                type: "firewallUpdateServiceState",
                firewallRequestData: {
                    serviceState: {
                        service: name,
                        state: editState
                    }
                }
            }).then(() => setEditState(undefined))
        }
    }, [editState, setEditState]);

    return (
        <Accordion key={name}>
            <AccordionSummary
                expandIcon={<ExpandMoreIcon/>}
                aria-controls="panel1a-content"
                id="panel1a-header"
            >
                <div>
                    <div>
                        <span style={{
                            fontWeight: "bold",
                            fontSize: "140%"
                        }}>{name}</span>
                    </div>
                </div>
            </AccordionSummary>
            <AccordionDetails>
                <div>
                    <ButtonGroup>
                        <Button variant={'contained'} onClick={() => setEditState(state)}
                                disabled={!!editState}
                        >Edit</Button>
                        <Button onClick={() => setEditState(undefined)}
                                disabled={!editState}
                        >Cancel</Button>
                        <Button variant={'contained'}
                                disabled={!editState}
                                onClick={submit}
                        >Submit</Button>
                    </ButtonGroup>
                </div>


                <ul style={{listStyleType: 'none', paddingLeft: '0'}}>
                        {(editState ?? state).elements.map((element, index) =>
                            <li key={element.name ?? index.toString()}>
                                <ElementComponent key={index.toString()}
                                                  element={element}
                                                  path={[element.name ?? index.toString()]}
                                                  editFn={editState ? editElement : undefined}/>
                            </li>
                        )}
                </ul>

            </AccordionDetails>
        </Accordion>
    )
}

type ElementProps = {
    path: string[];
    element: FirewallElement;
    editFn?: (element: FirewallElement, path: string[]) => void;
    nullable?: boolean;
    number?: boolean;
}
const ElementComponent = ({element, editFn, path}: ElementProps) => {

    return <>
        {element.type === "string" &&
            <ElementComponentString element={element} editFn={editFn} path={path} nullable={false}/>}
        {element.type === "stringNullable" &&
            <ElementComponentString element={element} editFn={editFn} path={path} nullable={true}/>}
        {element.type === "boolean" && <ElementComponentBoolean element={element} editFn={editFn} path={path}/>}
        {element.type === "stringList" && <ElementComponentStringList element={element} editFn={editFn} path={path}/>}
        {element.type === "number" &&
            <ElementComponentString element={element} editFn={editFn} path={path} nullable={false} number={true}/>}
        {element.type === "elementList" && <ElementComponentElementList element={element} editFn={editFn} path={path}/>}
    </>
}

const ElementComponentString = ({nullable, path, element, editFn, number}: ElementProps) => {
    return <>
        {!element.writeable && <div>
            <span>{element.description ?? element.name ?? ''}: </span>
            <span>{element.value}</span>
        </div>}
        {element.writeable && <TextField
            error={nullable === false && !element.value}
            label={element.description ?? element.name ?? ''}
            value={element.value}
            disabled={!editFn}
            onChange={event => {
                let newValue = event.target.value.trim();
                if (number === true) {
                    if (!isNumber(newValue)) {
                        return;
                    }
                }

                editFn?.(({
                    ...element,
                    value: newValue
                }), path);
            }}
        />}

    </>
}

const ElementComponentBoolean = ({path, element, editFn}: ElementProps) => {
    return <>
        {element.description ?? element.name ?? ''}
        <Checkbox
            checked={element.value}
            disabled={!editFn || !element.writeable}
            onChange={(_, checked) => {
                editFn?.(({
                    ...element,
                    value: checked
                }), path);
            }}
        />
    </>
}

const ElementComponentStringList = ({path, element, editFn}: ElementProps) => {
    return (
        <>
            {element.name && !element.description && <h5>{element.name}:</h5>}
            {element.description && <h5>{element.description}:</h5>}

            <ul style={{listStyleType: 'none', paddingLeft: 0}}>
                {(element.value as string[]).map((value, index) => (
                    <li key={index.toString()}>
                        <TextField value={value} label={index.toString()}
                                   onChange={event => {
                                       let newValue = event.target.value.trim();
                                       editFn?.(({
                                           ...element,
                                           value: (element.value as string[]).map((v, i) => i === index ? newValue : v)
                                       }), path);
                                   }}
                        />
                        <IconButton aria-label="expand row" size="small" onClick={() => {
                            editFn?.(({
                                ...element,
                                value: (element.value as string[]).filter((_, i) => i !== index)
                            }), path);
                        }}>
                            <DeleteIcon/>
                        </IconButton>
                    </li>
                ))}
                {editFn && <Button onClick={() => {
                    editFn({
                        ...element,
                        value: [...(element.value as string[]), ""]
                    }, path)
                }}>Add</Button>}

            </ul>
        </>
    );
}

const ElementComponentElementList = ({path, element, editFn}: ElementProps) => {
    if (element.value === "ssh") {
        console.log(element);
    }
    return (<>
        {element.name && !element.description && <h5>{element.name}:</h5>}
        {element.description && <h5>{element.description}:</h5>}

        <ul style={{listStyleType: "none", paddingLeft: "10px"}}>
            {(element.value as FirewallElement[]).map((value, index) => (
                <li key={index.toString()}>
                    <ElementComponent
                        path={[...path, value.name ?? index.toString()]}
                        element={value}
                        editFn={editFn}
                        key={index.toString()}
                    />
                    {!!editFn && element.writeable &&
                        <IconButton aria-label="expand row" size="small" onClick={() => {
                            editFn?.(({
                                ...element,
                                value: (element.value as FirewallElement[]).filter((_, i) => i !== index)
                            }), path);
                        }}>
                            <DeleteIcon/>
                        </IconButton>
                    }

                </li>
            ))}

            {editFn && element.templateElement &&  <Button onClick={() => {
                editFn({
                    ...element,
                    value: [...(element.value as FirewallElement[]), element.templateElement]
                }, path)
            }}>Add</Button>}
        </ul>

    </>)
}