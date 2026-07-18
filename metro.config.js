import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {getDefaultConfig} = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

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
