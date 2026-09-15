import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import ts from 'typescript';
import React from 'react';
import {renderToString} from 'react-dom/server';
import * as catalog from '../data/datasetCatalog.ts';
import * as published from '../data/publishedDatasetCatalog.ts';

const descriptor = {slug:'test', displayName:'Test', minecraftVersion:'1.12.2', packVersion:'1', publicationId:'a'.repeat(64), previewAssetSetId:'b'.repeat(64), isDefault:true};
function load(relativePath, browser = false, native = false) {
  let reads = 0, writes = 0;
  const values = {'minecraft-recipe-tree-published-catalog-cache':JSON.stringify({datasets:[descriptor]}), 'minecraft-recipe-tree-theme':'light', 'minecraft-recipe-tree-font':'minecraft'};
  const storage = {getItem(key) { reads++; return values[key] ?? null; }, setItem() { writes++; }};
  const exports = {};
  const context = {exports, console, process:{env:{}}, URL, URLSearchParams,
    ...(browser || native ? {localStorage:storage} : {}),
    ...(browser ? {window:{localStorage:storage,location:{search:''}},document:{documentElement:{dataset:{}}}} : {}),
    require(name) {
      if (name === 'react') return {...React,default:React};
      if (name === 'react-native') return {Platform:{OS:native?'ios':'web'}};
      if (name === './datasetCatalog') return catalog;
      if (name === './publishedDatasetCatalog') return published;
      if (name === './localPackStorage') return {};
      throw new Error(`Unexpected dependency ${name}`);
    },
  };
  const source = readFileSync(new URL(relativePath,import.meta.url),'utf8');
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.React}}).outputText,context);
  return {api:exports, reads:()=>reads,writes:()=>writes};
}
test('cached browser catalog produces exactly the server loading markup on first render', () => {
  const render = browser => {
    const env = load('../data/DatasetCatalogContext.tsx',browser);
    const Probe = () => React.createElement('span',null,env.api.useDatasetCatalog().state.status);
    const html = renderToString(React.createElement(env.api.DatasetCatalogProvider,null,React.createElement(Probe)));
    assert.equal(env.reads(),0);
    assert.equal(env.writes(),0);
    return html;
  };
  assert.equal(render(false),'<span>loading</span>');
  assert.equal(render(true),render(false));
});
test('native still uses its cached catalog immediately', () => {
  const env = load('../data/DatasetCatalogContext.tsx',false,true);
  const Probe = () => React.createElement('span',null,env.api.useDatasetCatalog().state.status);
  assert.equal(renderToString(React.createElement(env.api.DatasetCatalogProvider,null,React.createElement(Probe))),'<span>ready</span>');
  assert.equal(env.reads(),1);
});
test('light theme and Minecraft font do not alter hydration markup or write defaults into storage', () => {
  const render = browser => {
    const env = load('./themePreference.tsx',browser);
    const Probe = () => { const value=env.api.useThemePreference(); return React.createElement('span',null,`${value.preference}:${value.minecraftFont}`); };
    const html = renderToString(React.createElement(env.api.ThemePreferenceProvider,null,React.createElement(Probe)));
    assert.equal(env.writes(),0);
    return html;
  };
  assert.equal(render(true),render(false));
});
