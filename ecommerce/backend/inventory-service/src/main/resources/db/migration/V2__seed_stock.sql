-- Stock for the sample catalogue of product-service (V2__seed_catalog.sql, same product ids and SKUs).
-- A few products are deliberately low (6, 10) or out of stock (17) to demonstrate those states.

INSERT INTO inventory_items (product_id, sku, quantity_on_hand, quantity_reserved, low_stock_threshold,
                             created_at, updated_at)
VALUES (1, 'PHN-IP15-128', 50, 0, 5, now(), now()),
       (2, 'PHN-S24-256', 35, 0, 5, now(), now()),
       (3, 'PHN-PX8-128', 20, 0, 5, now(), now()),
       (4, 'PHN-RN13-128', 120, 0, 10, now(), now()),
       (5, 'TAB-IPAD10-64', 25, 0, 5, now(), now()),
       (6, 'TAB-TABS9-128', 3, 0, 5, now(), now()),
       (7, 'LAP-MBA-M2', 18, 0, 5, now(), now()),
       (8, 'LAP-XPS13-I7', 8, 0, 3, now(), now()),
       (9, 'LAP-TPE14-I5', 15, 0, 5, now(), now()),
       (10, 'LAP-ROG-G14', 2, 0, 3, now(), now()),
       (11, 'LAP-LEG5-4060', 10, 0, 3, now(), now()),
       (12, 'LAP-NITRO5-3050', 22, 0, 5, now(), now()),
       (13, 'AUD-APP2-USBC', 60, 0, 10, now(), now()),
       (14, 'AUD-WH1000XM5', 30, 0, 5, now(), now()),
       (15, 'AUD-BUDS2PRO', 40, 0, 5, now(), now()),
       (16, 'AUD-JBLFLIP6', 45, 0, 5, now(), now()),
       (17, 'AUD-MARSHALL-EM2', 0, 0, 3, now(), now()),
       (18, 'ACC-ANK-65W', 80, 0, 10, now(), now()),
       (19, 'ACC-UGR-C2C', 200, 0, 20, now(), now()),
       (20, 'ACC-MXM3S', 35, 0, 5, now(), now()),
       (21, 'ACC-K380', 70, 0, 10, now(), now()),
       (22, 'ACC-KCK2', 25, 0, 5, now(), now()),
       (23, 'PHN-IP16-128', 0, 0, 5, now(), now()),
       (24, 'AUD-APP1', 0, 0, 5, now(), now());

-- Opening balance in the ledger so that on-hand quantities are fully explained by movements
INSERT INTO stock_movements (product_id, type, on_hand_change, reserved_change, on_hand_after, reserved_after,
                             reference, reason, created_at)
SELECT product_id, 'ADJUSTMENT', quantity_on_hand, 0, quantity_on_hand, 0, 'seed', 'Opening balance', now()
FROM inventory_items
WHERE quantity_on_hand > 0;
