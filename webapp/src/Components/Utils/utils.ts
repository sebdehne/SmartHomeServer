export const arrayBufferToBase64 = (buffer: ArrayBuffer) => {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
};

export const numberTo2Decimal = (n: number | null | undefined) => {
    if (n || n === 0) {
        return new Intl.NumberFormat('nb-NO', {maximumFractionDigits: 2, minimumFractionDigits: 2}).format(n)
    } else {
        return undefined;
    }
}

export const number2Decimal = (n: number | null | undefined) => {
    if (n || n === 0) {
        return Math.round((n + Number.EPSILON) * 10) / 10;
    } else {
        return undefined;
    }
}

export const numberNok = (n: number | null | undefined) => {
    if (n) {
        return new Intl.NumberFormat('nb-NO', {
            style: 'currency',
            currency: 'NOK'
        }).format(n)
    } else {
        return undefined;
    }
}

export const eesAlarms = (alarms: string[]) => {
    if (alarms.length < 1) {
        return null;
    } else {
        return alarms.join(",")
    }
}

export const isNumber = (str: string): boolean => {
    return !isNaN(parseInt(str.trim()));
}