export const formateDateTime = (timestamp: number | null) => {
    if (timestamp === null) {
        return null;
    } else {
        return new Date(timestamp).toLocaleString();
    }
}