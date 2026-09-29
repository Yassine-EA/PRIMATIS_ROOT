import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';


export const PrimatisPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eaf1f5',
      100: '#cfe0e9',
      200: '#a8c7d6',
      300: '#7ba9c0',
      400: '#4a87a3',
      500: '#2c6a87',
      600: '#1a4f72', // Navy 600 (v2)
      700: '#123f5e', // Navy 700 (v2)
      800: '#0b3650', // Navy 800 (v2)
      900: '#082b40', // Navy 900 (v2)
      950: '#051c2a',
    },
    surface: {
      0: '#ffffff',
      50: '#faf9f6', // Ivory 50 (v2)
      100: '#f6f1e8', // Ivory 100 (v2)
      200: '#ece5da', // Ivory 200 (v2)
      300: '#cfc3a8',
      400: '#b0a17e',
      500: '#8f8163',
      600: '#6e624a',
      700: '#524939',
      800: '#38322a',
      900: '#211d18',
      950: '#14110e',
    },
  },
});
