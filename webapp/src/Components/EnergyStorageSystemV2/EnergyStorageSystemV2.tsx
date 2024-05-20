import {Container} from "@mui/material";
import Header from "../Header";
import React from "react";
import {NamedComponent} from "./drawingComponents/NamedComponent";


export const EnergyStorageSystemV2 = () => {
    return (<Container maxWidth="sm" className="App">
        <Header
            title={"Energy storage system V2"}
            sending={false}
        />

        <div>
            <Drawing/>
        </div>

    </Container>)
}

const Drawing = () => {
    return (<svg width="625" height="625" xmlns="http://www.w3.org/2000/svg">
        <NamedComponent
            mainColor={"#8a3838"}
            labelColor={"#522020"}
            x={50}
            y={50}
            numberOfHolesLeft={1}
            numberOfHolesRight={2}
            numberOfHolesHorizontal={2}
            labelPlacement={"up"}
        />
    </svg>)
}