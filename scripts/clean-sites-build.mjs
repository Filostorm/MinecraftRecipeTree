import {rm} from 'node:fs/promises';
import {join} from 'node:path';

const outputDirectory = join(process.cwd(), 'dist');
console.log(`Removing prior production output at ${outputDirectory}`);
await rm(outputDirectory, {recursive: true, force: true});
