
export function sameIdSet(a: readonly { id: number }[], b: readonly { id: number }[]): boolean {
  const setA = new Set(a.map((item) => item.id));
  const setB = new Set(b.map((item) => item.id));
  return setA.size === setB.size && [...setA].every((id) => setB.has(id));
}
