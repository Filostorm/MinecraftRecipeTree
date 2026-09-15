import {migrateLegacyNativeLocalStorage} from './nativeLocalStorage';

// Dependency evaluation must finish the migration before App imports read saved preferences.
migrateLegacyNativeLocalStorage();
