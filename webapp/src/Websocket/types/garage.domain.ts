export enum HoermannE4Command {
    Nop = 0,
    Open = 1,
    Close = 2,
    Toggle = 3,
    Light = 4,
    Vent = 5,
    HalvOpen = 6,
}

export type HoermannE4Broadcast = {
    targetPos: number;
    currentPos: number;
    doorState: string;
    isVented: boolean;
    motorSpeed: number;
    light: boolean;
    motorRunning: boolean;
    ageInMs: number;
    receivedAt: string;
}

export enum SupramatiDoorState {
    STOPPED = "STOPPED",
    OPENING = "OPENING",
    HALV_OPENING = "HALV_OPENING",
    VENTING = "VENTING",
    OPEN = "OPEN",
    CLOSED = "CLOSED",
    HALV_OPEN = "HALV_OPEN",
}
