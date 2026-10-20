/**
 * Catalogue presentation constants.
 *
 * These are *presentation* limits, not business rules: how many items a page
 * renders before offering "see all". They live here so the home page, category
 * pages and search page cannot drift apart.
 */

/** Items shown in the home page grid before the "see all" link. */
export const CATALOGUE_PREVIEW_COUNT = 12;

/** Items per rail on the home page. */
export const POPULAR_COUNT = 10;

/** Items shown in the "Buy again" rail on the home page. */
export const BUY_AGAIN_COUNT = 8;

/** Default page size for the browse/search grids. */
export const BROWSE_PAGE_SIZE = 24;

/** Related products on a product page. */
export const RELATED_COUNT = 8;

/**
 * Search terms the catalogue actually contains, used to power "no results"
 * suggestions. Kept short and derived from the real seed data so the suggestion
 * can never point at something we do not stock.
 */
export const SEARCH_SUGGESTIONS = [
  "Apple",
  "Banana",
  "Tomato",
  "Onion",
  "Potato",
  "Palak",
  "Coriander",
  "Lemon",
  "Mango",
  "Ginger",
] as const;

/** Received-search storage key (client-only, never sent anywhere). */
export const RECENT_SEARCH_KEY = "vedant.search.recent.v1";
