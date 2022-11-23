export const formateDateTime = (timestamp: number | null | undefined) => {
    if (!timestamp) {
        return null;
    } else {
        return new Date(timestamp).toLocaleString();
    }
}

export function formatTime(isoTimeString: string) {
    const d = new Date(isoTimeString);
    let h = d.getHours();
    let hStr = "";
    if (h < 10) {
        hStr = "0" + h;
    } else {
        hStr = h.toString();
    }
    let m = d.getMinutes();
    let mStr = "";
    if (m < 10) {
        mStr = "0" + m;
    } else {
        mStr = m.toString();
    }
    return hStr + ":" + mStr;
}

export function currentDay() {
    return new Date().getDate();
}