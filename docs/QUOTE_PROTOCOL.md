# Land quote, management, and initial grant integration

Optional `landmod:quote_request` / `quote_response` packets do not change existing protocols.
They carry selection generation, operation and bounded JSON. Quotes never debit or transfer.
The server executes the market API on its server thread. Management requires permission level 2,
and the market must check this independently. Old clients still use commands/chest confirmation.

## Market API
- `LandMarketTerminal.quote(MinecraftServer, UUID viewer, String dimension, int[] xz)` returns JSON.
  `total`, `currency`, `currencyName`, `chunks`, `buyable`, `reason`, `listingId`, `revision`, `scope`.
  Amount and chunks describe the FULL transaction, not each selected cell.
- `LandMarketTerminal.manage(MinecraftServer, UUID operator, String json)` returns JSON.
  Read: `{"action":"get"}`. Save:
  `{"action":"set","enabled":false,"price":"10","currency":"note"}`.
  Prices are positive integers, matching the current market settlement units. Config must be persisted
  by market. Return `enabled`, `price`, `currency`, `reason` plus optional available currencies.
- `LandMarketTerminal.open(player, null, "buy", dimension, xz)` opens initial-sale confirmation.

The client invalidates stale selection generations and refreshes every 100 ticks. Purchase enters
market confirmation, which revalidates price, revision, ownership, total scope, and balance.
Missing APIs show an explicit error, never a fabricated free price. Defaults do not reprice listings.

## Initial grant API
- `LandApi.initialGrantCheck(server,buyer,dimension,xz)`: `ok` or `rejected:<reason>`.
- `LandApi.initialGrant(server,buyer,dimension,xz,reference)`:
  `applied`, `pending`, `conflict`, or `rejected:<reason>`.
- `LandApi.initialGrantStatus(server,reference)`: `applied`, `pending`, or `missing`.

Only a definite `rejected` permits a checkout refund. Pending, conflict or exceptions require
reconciliation without refund: land may already have been granted. Persist checkout/payment intent
before grant. A retry uses the same reference, buyer and exact scope. Receipts bind sorted scope
and buyer, survive NBT reload, and do not grant again after ownership later changes.

The complete scope is validated for limits, duplicate coordinates, world border, existing claims,
and protection gates before mutation. It is granted as one parcel. Claims and receipt share an
atomic replacement file; success is acknowledged only after the receipt is read back. Unsupported
atomic moves/I/O failures remain pending, never an excuse to silently refund a successful grant.

Required integration tests still include Fabric compilation, filesystem fault injection, payment
recovery across restart, stale quote reordering, and Xaero layout at multiple GUI scales.
