/** every scene, keyed as `V3ArtKind` names (lower case), and the Kotlin file each one is written to */
import { offline } from './offline.mjs';
import { cloud } from './cloud.mjs';
import { mark } from './mark.mjs';
import { products } from './products.mjs';
import { sales } from './sales.mjs';
import { people } from './people.mjs';
import { orders } from './orders.mjs';
import { stock } from './stock.mjs';
import { search } from './search.mjs';
import { tree } from './tree.mjs';

export const SCENES = {
  tree,
  products,
  sales,
  people,
  orders,
  stock,
  search,
  error_cloud: cloud,
  error_cable: offline,
  error_mark: mark,
};

export const FILES = {
  tree: 'Tree',
  products: 'Products',
  sales: 'Sales',
  people: 'People',
  orders: 'Orders',
  stock: 'Stock',
  search: 'Search',
  error_cloud: 'Cloud',
  error_cable: 'Offline',
  error_mark: 'Mark',
};
