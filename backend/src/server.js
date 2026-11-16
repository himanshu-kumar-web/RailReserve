import { createApp } from './app.js';
import { RailCoreClient } from './provider/railCoreClient.js';

if (![process.env.RAILCORE_API_KEY, process.env.RAILCORE_API_KEY_2, process.env.RAILCORE_API_KEY_3]
  .some((key) => key?.trim())) {
  process.stderr.write('At least one RailCore API key is required to start the RailReserve backend.\n');
  process.exitCode = 1;
} else {
  const port = Number(process.env.PORT || 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    process.stderr.write('PORT must be a valid TCP port.\n');
    process.exitCode = 1;
  } else {
    const app = createApp({ client: new RailCoreClient() });
    app.listen(port, '0.0.0.0', () => process.stdout.write(`RailReserve backend listening on port ${port}.\n`));
  }
}
