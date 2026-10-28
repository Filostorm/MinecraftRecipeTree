import type {Plugin} from 'vite';

export function appVersionPlugin(buildId: string): Plugin {
  const source = JSON.stringify({buildId});
  return {
    name: 'mrt-app-version',
    generateBundle() {
      if (this.environment.name === 'client') {
        this.emitFile({type: 'asset', fileName: 'app-version.json', source});
      }
    },
    configureServer(server) {
      server.middlewares.use('/app-version.json', (_request, response) => {
        response.setHeader('Content-Type', 'application/json');
        response.setHeader('Cache-Control', 'no-store');
        response.end(source);
      });
    },
  };
}
