export const formateDateTime = (timestamp: number | null | undefined) => {
    if (!timestamp) {
        return null;
    } else {
        return new Date(timestamp).toLocaleString();
    }
}
