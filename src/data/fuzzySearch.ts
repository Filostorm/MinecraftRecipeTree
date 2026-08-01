const SEARCH_SEPARATOR = /[^\p{L}\p{N}]+/gu;
const MAX_FUZZY_CANDIDATES = 12_000;

export type FuzzyCandidateIndex = {
  bigrams: Map<string, number[]>;
  shapes: Map<string, number[]>;
};

export function normalizeSearchText(value: string): string {
  return value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase()
    .replace(SEARCH_SEPARATOR, ' ')
    .trim();
}

/**
 * Returns a lower-is-better fuzzy score, or null when the query does not match.
 * Callers should normalize and cache fields when searching large catalogs.
 */
export function fuzzySearchScore(
  normalizedQuery: string,
  normalizedFields: readonly string[],
  preparedQueryWords?: readonly string[],
  preparedCandidateWords?: readonly string[],
): number | null {
  if (!normalizedQuery) return 0;

  const directScore = directSearchScore(normalizedQuery, normalizedFields);
  if (directScore != null) return directScore;

  const queryWords = preparedQueryWords ?? normalizedQuery.split(' ').filter(Boolean);
  const candidateWords =
    preparedCandidateWords ?? normalizedFields.flatMap(field => field.split(' ').filter(Boolean));
  const primaryWords = normalizedFields[0]?.split(' ').filter(Boolean) ?? [];
  let score = 50;
  let primaryFieldMatches = true;

  for (const queryWord of queryWords) {
    const bestWordScore = fuzzyWordScore(queryWord, candidateWords);
    if (!Number.isFinite(bestWordScore)) return null;
    if (!Number.isFinite(fuzzyWordScore(queryWord, primaryWords))) primaryFieldMatches = false;
    score += bestWordScore;
  }

  const primaryFieldLength = normalizedFields[0]?.length ?? normalizedQuery.length;
  return (
    score +
    (primaryFieldMatches ? 0 : 20) +
    Math.min(20, Math.abs(primaryFieldLength - normalizedQuery.length))
  );
}

function fuzzyWordScore(queryWord: string, candidateWords: readonly string[]): number {
  let bestWordScore = Infinity;
  for (const candidateWord of candidateWords) {
    if (candidateWord === queryWord) return 0;
    if (candidateWord.startsWith(queryWord)) {
      bestWordScore = Math.min(bestWordScore, 2 + candidateWord.length - queryWord.length);
      continue;
    }
    const innerIndex = candidateWord.indexOf(queryWord);
    if (innerIndex >= 0) {
      bestWordScore = Math.min(bestWordScore, 10 + innerIndex);
      continue;
    }

    const subsequenceGap =
      queryWord.length >= 3 && candidateWord.length <= queryWord.length + 4
        ? orderedSubsequenceGap(queryWord, candidateWord)
        : null;
    if (subsequenceGap != null) {
      bestWordScore = Math.min(bestWordScore, 15 + subsequenceGap);
      continue;
    }

    const maxDistance = queryWord.length <= 2 ? 0 : queryWord.length <= 5 ? 1 : 2;
    if (maxDistance > 0 && Math.abs(candidateWord.length - queryWord.length) <= maxDistance) {
      const distance = boundedDamerauLevenshtein(queryWord, candidateWord, maxDistance);
      if (distance <= maxDistance) {
        bestWordScore = Math.min(bestWordScore, 20 + distance * 5);
      }
    }
  }
  return bestWordScore;
}

export function directSearchScore(
  normalizedQuery: string,
  normalizedFields: readonly string[],
): number | null {
  let directScore: number | null = null;
  for (let fieldIndex = 0; fieldIndex < normalizedFields.length; fieldIndex += 1) {
    const field = normalizedFields[fieldIndex];
    if (!field) continue;
    if (field === normalizedQuery) return fieldIndex;
    if (field.startsWith(normalizedQuery)) {
      directScore = Math.min(directScore ?? Infinity, 10 + fieldIndex);
      continue;
    }
    const phraseIndex = field.indexOf(normalizedQuery);
    if (phraseIndex >= 0) {
      directScore = Math.min(directScore ?? Infinity, 20 + fieldIndex + phraseIndex);
    }
  }
  return directScore;
}

export function buildFuzzyCandidateIndex(
  candidateWordsByIndex: readonly (readonly string[])[],
): FuzzyCandidateIndex {
  const bigrams = new Map<string, number[]>();
  const shapes = new Map<string, number[]>();
  candidateWordsByIndex.forEach((words, itemIndex) => {
    const itemBigrams = new Set<string>();
    const itemShapes = new Set<string>();
    for (const word of words) {
      if (!word) continue;
      itemShapes.add(`${word[0]}:${word.length}`);
      for (let index = 0; index < word.length - 1; index += 1) {
        itemBigrams.add(word.slice(index, index + 2));
      }
    }
    for (const bigram of itemBigrams) appendIndex(bigrams, bigram, itemIndex);
    for (const shape of itemShapes) appendIndex(shapes, shape, itemIndex);
  });
  return {bigrams, shapes};
}

export function fuzzyCandidateIndices(
  queryWords: readonly string[],
  index: FuzzyCandidateIndex,
  acceptIndex?: (itemIndex: number) => boolean,
): number[] {
  const pools = queryWords.map(word => {
    const postings: number[][] = [];
    const keys = new Set<string>();
    for (let offset = 0; offset < word.length - 1; offset += 1) {
      keys.add(word.slice(offset, offset + 2));
    }
    const nearbyLengths = [0, 1, -1, 2, -2, 3, 4]
      .map(offset => word.length + offset)
      .filter(length => length > 0);
    for (const initial of [word[0], word[1]]) {
      if (!initial) continue;
      for (const length of nearbyLengths) {
        const posting = index.shapes.get(`${initial}:${length}`);
        if (posting) postings.push(posting);
      }
    }
    for (const key of keys) {
      const posting = index.bigrams.get(key);
      if (posting) postings.push(posting);
    }
    return postings;
  });
  pools.sort(
    (left, right) =>
      left.reduce((total, posting) => total + posting.length, 0) -
      right.reduce((total, posting) => total + posting.length, 0),
  );

  const candidates = new Set<number>();
  for (const posting of pools[0] ?? []) {
    for (const itemIndex of posting) {
      if (acceptIndex && !acceptIndex(itemIndex)) continue;
      candidates.add(itemIndex);
      if (candidates.size >= MAX_FUZZY_CANDIDATES) return [...candidates];
    }
  }
  return [...candidates];
}

function appendIndex(index: Map<string, number[]>, key: string, itemIndex: number): void {
  const posting = index.get(key);
  if (posting) posting.push(itemIndex);
  else index.set(key, [itemIndex]);
}

function orderedSubsequenceGap(needle: string, haystack: string): number | null {
  let needleIndex = 0;
  let firstMatch = -1;
  let lastMatch = -1;
  for (let index = 0; index < haystack.length && needleIndex < needle.length; index += 1) {
    if (haystack[index] !== needle[needleIndex]) continue;
    if (firstMatch < 0) firstMatch = index;
    lastMatch = index;
    needleIndex += 1;
  }
  if (needleIndex !== needle.length) return null;
  return firstMatch + (lastMatch - firstMatch + 1 - needle.length);
}

function boundedDamerauLevenshtein(a: string, b: string, limit: number): number {
  if (a === b) return 0;
  if (Math.abs(a.length - b.length) > limit) return limit + 1;

  let previousPrevious = Array<number>(b.length + 1).fill(limit + 1);
  let previous = Array.from({length: b.length + 1}, (_, index) => index);

  for (let i = 1; i <= a.length; i += 1) {
    const current = Array<number>(b.length + 1).fill(limit + 1);
    current[0] = i;
    let rowMinimum = limit + 1;
    const firstColumn = Math.max(1, i - limit);
    const lastColumn = Math.min(b.length, i + limit);
    for (let j = firstColumn; j <= lastColumn; j += 1) {
      const substitution = previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1);
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, substitution);
      if (
        i > 1 &&
        j > 1 &&
        a[i - 1] === b[j - 2] &&
        a[i - 2] === b[j - 1]
      ) {
        current[j] = Math.min(current[j], previousPrevious[j - 2] + 1);
      }
      rowMinimum = Math.min(rowMinimum, current[j]);
    }
    if (rowMinimum > limit) return limit + 1;
    previousPrevious = previous;
    previous = current;
  }

  return previous[b.length];
}
