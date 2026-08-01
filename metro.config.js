import {createRequire} from 'node:module';
import {dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const require = createRequire(import.meta.url);
const {getDefaultConfig} = require('expo/metro-config');
const projectRoot = dirname(fileURLToPath(import.meta.url));

const config = getDefaultConfig(projectRoot);

// Vinext requires React 19.2.8 for the website, while React Native 0.85's
// renderer requires React 19.2.3 exactly. Keep the native bundle on Expo 56's
// compatible React without forcing the web application onto an invalid peer set.
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (
    platform !== 'web' &&
    (moduleName === 'react' || moduleName.startsWith('react/'))
  ) {
    const nativeReactModule = moduleName.replace(/^react/, 'react-native-react');
    return context.resolveRequest(context, nativeReactModule, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

// public/exports holds thousands of exported PNGs/JSONs; they're served statically and
// must stay out of Metro's module crawler/watcher or rebuilds stall after a data copy.
const exportsData = /public\/exports\/.*/;
const prior = config.resolver.blockList;
config.resolver.blockList = Array.isArray(prior)
  ? [...prior, exportsData]
  : prior
    ? [prior, exportsData]
    : exportsData;

export default config;
