import {createTheme} from '@mui/material/styles';
// A custom theme for this app
export const theme = createTheme({
    palette: {
        mode: 'dark',
        primary: {
            main: '#2457ff',
            contrastText: '#ffffff',
        },
        secondary: {
            main: '#ff006a',
            contrastText: '#ffffff',
        }
    },
});