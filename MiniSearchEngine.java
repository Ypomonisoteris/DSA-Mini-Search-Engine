import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * MINI SEARCH ENGINE - DSA Final Project (Group 9)
 * =================================================
 * Local-file keyword search with TF-IDF ranking. No crawling, no AI.
 *
 * DSA topics used (12):
 *   1. Arrays ............ documents store, token lists, postings arrays
 *   2. Linked Lists ...... postings lists + hash-table chains
 *   3. Hashing ........... custom hash table (djb2) = the inverted index
 *   4. Trees ............. Trie for prefix autocomplete
 *   5. Heaps ............. min-heap for Top-K ranking
 *   6. Sorting ........... merge sort for final result order
 *   7. Searching ......... binary search for AND-intersection of postings
 *   8. Recursion ......... merge sort + trie word collection
 *   9. Stacks ............ search history with /back
 *  10. Queues ............ FIFO queue for file loading and BFS
 *  11. Graphs + BFS ...... "related documents" graph, BFS to depth 2
 *  12. Dynamic Prog. ..... Levenshtein edit distance for "Did you mean?"
 *
 * Run:  java MiniSearchEngine.java          (interactive, Java 11+)
 *       java MiniSearchEngine.java --demo   (scripted demo)
 *   or: javac MiniSearchEngine.java && java MiniSearchEngine
 */
public class MiniSearchEngine {

    static final String DOCS_DIR = "documents";
    static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "it", "for",
            "on", "with", "as", "by", "at", "are", "this", "that", "be", "can"));

    // ------------------------------------------------------------------
    // TEXT PROCESSING (arrays)
    // ------------------------------------------------------------------
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String w : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!w.isEmpty() && !STOPWORDS.contains(w)) tokens.add(w);
        }
        return tokens;
    }

    // ------------------------------------------------------------------
    // LINKED LIST: postings list (doc id -> term frequency)
    // ------------------------------------------------------------------
    static class PostingNode {
        int docId, tf;
        PostingNode next;
        PostingNode(int docId) { this.docId = docId; this.tf = 1; }
    }

    static class PostingList {
        PostingNode head, tail;
        int size = 0;

        /** O(1) append. Docs are indexed in id order, so a repeat can only be at the tail. */
        void add(int docId) {
            if (tail != null && tail.docId == docId) { tail.tf++; return; }
            PostingNode node = new PostingNode(docId);
            if (tail == null) head = node; else tail.next = node;
            tail = node;
            size++;
        }

        /** Returns {sorted docIds, tfs}. */
        int[][] toArrays() {
            int[] ids = new int[size], tfs = new int[size];
            int i = 0;
            for (PostingNode cur = head; cur != null; cur = cur.next, i++) {
                ids[i] = cur.docId;
                tfs[i] = cur.tf;
            }
            return new int[][]{ids, tfs};
        }
    }

    // ------------------------------------------------------------------
    // HASH TABLE with separate chaining (linked list buckets)
    // ------------------------------------------------------------------
    static class Entry {
        String key;
        PostingList value;
        Entry next;
        Entry(String key, PostingList value) { this.key = key; this.value = value; }
    }

    static class HashTable {
        Entry[] buckets = new Entry[64];
        int count = 0;

        static long hash(String key) {                 // djb2 string hash
            long h = 5381;
            for (int i = 0; i < key.length(); i++) {
                h = ((h << 5) + h + key.charAt(i)) & 0xFFFFFFFFL;
            }
            return h;
        }

        int index(String key) { return (int) (hash(key) % buckets.length); }

        PostingList get(String key) {
            for (Entry cur = buckets[index(key)]; cur != null; cur = cur.next) {
                if (cur.key.equals(key)) return cur.value;
            }
            return null;
        }

        void put(String key, PostingList value) {
            int i = index(key);
            for (Entry cur = buckets[i]; cur != null; cur = cur.next) {
                if (cur.key.equals(key)) { cur.value = value; return; }
            }
            Entry node = new Entry(key, value);
            node.next = buckets[i];
            buckets[i] = node;
            count++;
            if ((double) count / buckets.length > 0.75) resize();   // keep load factor low
        }

        void resize() {
            Entry[] old = buckets;
            buckets = new Entry[old.length * 2];
            count = 0;
            for (Entry head : old) {
                for (Entry cur = head; cur != null; cur = cur.next) put(cur.key, cur.value);
            }
        }

        List<String> keys() {
            List<String> out = new ArrayList<>();
            for (Entry head : buckets) {
                for (Entry cur = head; cur != null; cur = cur.next) out.add(cur.key);
            }
            return out;
        }
    }

    // ------------------------------------------------------------------
    // TRIE (tree) + RECURSION: prefix autocomplete
    // ------------------------------------------------------------------
    static class TrieNode {
        TrieNode[] children = new TrieNode[36];        // 0-9, a-z
        boolean isWord;
    }

    static class Trie {
        TrieNode root = new TrieNode();

        static int idx(char c) { return c <= '9' ? c - '0' : 10 + (c - 'a'); }
        static char chr(int i) { return i < 10 ? (char) ('0' + i) : (char) ('a' + i - 10); }

        void insert(String word) {
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                int i = idx(c);
                if (node.children[i] == null) node.children[i] = new TrieNode();
                node = node.children[i];
            }
            node.isWord = true;
        }

        List<String> autocomplete(String prefix, int limit) {
            List<String> results = new ArrayList<>();
            TrieNode node = root;
            for (char c : prefix.toCharArray()) {
                if (!Character.isLetterOrDigit(c) || c > 'z') return results;
                node = node.children[idx(c)];
                if (node == null) return results;
            }
            collect(node, prefix, results, limit);      // recursion
            return results;
        }

        void collect(TrieNode node, String path, List<String> results, int limit) {
            if (results.size() >= limit) return;
            if (node.isWord) results.add(path);
            for (int i = 0; i < 36; i++) {
                if (node.children[i] != null) collect(node.children[i], path + chr(i), results, limit);
            }
        }
    }

    // ------------------------------------------------------------------
    // MIN-HEAP: Top-K ranking in O(n log k)
    // ------------------------------------------------------------------
    static class Scored implements Comparable<Scored> {
        double score;
        int docId;
        Scored(double score, int docId) { this.score = score; this.docId = docId; }
        public int compareTo(Scored o) {
            int c = Double.compare(score, o.score);
            return c != 0 ? c : Integer.compare(docId, o.docId);
        }
    }

    static class MinHeap {
        List<Scored> a = new ArrayList<>();

        int size() { return a.size(); }
        Scored peek() { return a.get(0); }

        void push(Scored item) {
            a.add(item);
            int i = a.size() - 1;
            while (i > 0 && a.get(i).compareTo(a.get((i - 1) / 2)) < 0) {
                swap(i, (i - 1) / 2);
                i = (i - 1) / 2;
            }
        }

        Scored pop() {
            Scored top = a.get(0);
            Scored last = a.remove(a.size() - 1);
            if (!a.isEmpty()) {
                a.set(0, last);
                int i = 0, n = a.size();
                while (true) {
                    int l = 2 * i + 1, r = 2 * i + 2, s = i;
                    if (l < n && a.get(l).compareTo(a.get(s)) < 0) s = l;
                    if (r < n && a.get(r).compareTo(a.get(s)) < 0) s = r;
                    if (s == i) break;
                    swap(i, s);
                    i = s;
                }
            }
            return top;
        }

        void swap(int i, int j) { Scored t = a.get(i); a.set(i, a.get(j)); a.set(j, t); }
    }

    /** Keep only the k best items. */
    static List<Scored> topK(List<Scored> items, int k) {
        MinHeap heap = new MinHeap();
        for (Scored item : items) {
            if (heap.size() < k) heap.push(item);
            else if (item.compareTo(heap.peek()) > 0) { heap.pop(); heap.push(item); }
        }
        return heap.a;
    }

    // ------------------------------------------------------------------
    // MERGE SORT (sorting + recursion)
    // ------------------------------------------------------------------
    static <T> List<T> mergeSort(List<T> list, Comparator<T> cmp) {
        if (list.size() <= 1) return list;
        int mid = list.size() / 2;
        List<T> left = mergeSort(new ArrayList<>(list.subList(0, mid)), cmp);
        List<T> right = mergeSort(new ArrayList<>(list.subList(mid, list.size())), cmp);
        List<T> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            if (cmp.compare(left.get(i), right.get(j)) <= 0) out.add(left.get(i++));
            else out.add(right.get(j++));
        }
        while (i < left.size()) out.add(left.get(i++));
        while (j < right.size()) out.add(right.get(j++));
        return out;
    }

    // ------------------------------------------------------------------
    // BINARY SEARCH: AND-intersection of sorted postings arrays
    // ------------------------------------------------------------------
    static int binarySearch(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (arr[mid] == target) return mid;
            if (arr[mid] < target) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    /** Docs present in ALL sorted arrays: scan the shortest, binary-search the rest. */
    static Set<Integer> intersect(List<int[]> lists) {
        List<int[]> sorted = mergeSort(new ArrayList<>(lists), Comparator.comparingInt(x -> x.length));
        Set<Integer> result = new LinkedHashSet<>();
        for (int d : sorted.get(0)) {
            boolean inAll = true;
            for (int k = 1; k < sorted.size() && inAll; k++) {
                if (binarySearch(sorted.get(k), d) == -1) inAll = false;
            }
            if (inAll) result.add(d);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // STACK (search history) and QUEUE (file loading, BFS)
    // ------------------------------------------------------------------
    static class Stack<T> {
        private final List<T> items = new ArrayList<>();
        void push(T x) { items.add(x); }
        T pop() { return items.isEmpty() ? null : items.remove(items.size() - 1); }
        List<T> newestFirst() {
            List<T> out = new ArrayList<>(items);
            Collections.reverse(out);
            return out;
        }
    }

    static class Queue<T> {
        private final List<T> items = new ArrayList<>();
        private int head = 0;
        void enqueue(T x) { items.add(x); }
        T dequeue() { return items.get(head++); }
        boolean isEmpty() { return head >= items.size(); }
    }

    // ------------------------------------------------------------------
    // DYNAMIC PROGRAMMING: edit distance for "Did you mean?"
    // ------------------------------------------------------------------
    static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    // ------------------------------------------------------------------
    // SEARCH ENGINE
    // ------------------------------------------------------------------
    static class Document {
        int id;
        String name, text;
        List<String> tokens;
        Document(int id, String name, String text) {
            this.id = id; this.name = name; this.text = text; this.tokens = tokenize(text);
        }
    }

    static class SearchResult {
        List<Scored> ranked = new ArrayList<>();
        List<String[]> hints = new ArrayList<>();      // {typed, suggestion}
    }

    static class Engine {
        List<Document> docs = new ArrayList<>();
        HashTable index = new HashTable();
        Trie trie = new Trie();
        List<List<Integer>> graph = new ArrayList<>();
        Stack<String> history = new Stack<>();

        // ---- indexing ----
        void loadFolder(Path folder) throws IOException {
            Queue<Path> q = new Queue<>();
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(folder)) {
                stream.filter(p -> p.toString().endsWith(".txt")).forEach(files::add);
            }
            Collections.sort(files);
            for (Path p : files) q.enqueue(p);
            while (!q.isEmpty()) {                     // FIFO processing
                Path p = q.dequeue();
                indexDoc(new Document(docs.size(), p.getFileName().toString(), Files.readString(p)));
            }
            buildGraph();
        }

        void indexDoc(Document doc) {
            docs.add(doc);
            for (String term : doc.tokens) {
                PostingList plist = index.get(term);
                if (plist == null) {
                    plist = new PostingList();
                    index.put(term, plist);
                    trie.insert(term);
                }
                plist.add(doc.id);
            }
        }

        double idf(String term) {
            PostingList p = index.get(term);
            return p == null ? 0.0 : Math.log((1.0 + docs.size()) / (1.0 + p.size)) + 1;
        }

        // ---- graph of related documents ----
        Set<String> keywords(Document doc, int n) {
            Map<String, Integer> tf = new LinkedHashMap<>();
            for (String t : doc.tokens) tf.merge(t, 1, Integer::sum);
            MinHeap heap = new MinHeap();
            Map<Integer, String> byId = new HashMap<>();
            int id = 0;
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                byId.put(id, e.getKey());
                Scored s = new Scored(e.getValue() * idf(e.getKey()), id++);
                if (heap.size() < n) heap.push(s);
                else if (s.compareTo(heap.peek()) > 0) { heap.pop(); heap.push(s); }
            }
            Set<String> out = new HashSet<>();
            for (Scored s : heap.a) out.add(byId.get(s.docId));
            return out;
        }

        void buildGraph() {
            List<Set<String>> kws = new ArrayList<>();
            for (Document d : docs) { kws.add(keywords(d, 5)); graph.add(new ArrayList<>()); }
            for (int i = 0; i < docs.size(); i++) {
                for (int j = i + 1; j < docs.size(); j++) {
                    int shared = 0;
                    for (String k : kws.get(i)) if (kws.get(j).contains(k)) shared++;
                    if (shared >= 2) { graph.get(i).add(j); graph.get(j).add(i); }
                }
            }
        }

        /** BFS up to `depth` hops. Returns list of {docId, hops}. */
        List<int[]> related(int start, int depth) {
            List<int[]> found = new ArrayList<>();
            Set<Integer> visited = new HashSet<>();
            visited.add(start);
            Queue<int[]> q = new Queue<>();
            q.enqueue(new int[]{start, 0});
            while (!q.isEmpty()) {
                int[] cur = q.dequeue();
                if (cur[1] == depth) continue;
                for (int nb : graph.get(cur[0])) {
                    if (visited.add(nb)) {
                        found.add(new int[]{nb, cur[1] + 1});
                        q.enqueue(new int[]{nb, cur[1] + 1});
                    }
                }
            }
            return found;
        }

        // ---- searching ----
        String suggest(String term) {
            String best = null;
            int bestD = 3;
            for (String w : index.keys()) {
                if (Math.abs(w.length() - term.length()) <= 2) {
                    int d = editDistance(term, w);
                    if (d < bestD) { best = w; bestD = d; }
                }
            }
            return best;
        }

        SearchResult search(String query, int k, boolean requireAll) {
            SearchResult res = new SearchResult();
            List<String> terms = tokenize(query);
            Map<String, int[][]> arrays = new LinkedHashMap<>();
            for (String t : terms) {
                PostingList plist = index.get(t);
                if (plist == null) {
                    String alt = suggest(t);
                    if (alt != null) res.hints.add(new String[]{t, alt});
                } else arrays.put(t, plist.toArrays());
            }
            if (arrays.isEmpty()) return res;

            Set<Integer> candidates = null;
            if (requireAll) {
                if (arrays.size() < new HashSet<>(terms).size()) return res;
                List<int[]> idLists = new ArrayList<>();
                for (int[][] a : arrays.values()) idLists.add(a[0]);
                candidates = intersect(idLists);
            }

            Map<Integer, Double> scores = new LinkedHashMap<>();
            for (Map.Entry<String, int[][]> e : arrays.entrySet()) {
                double w = idf(e.getKey());
                int[] ids = e.getValue()[0], tfs = e.getValue()[1];
                for (int i = 0; i < ids.length; i++) {
                    if (candidates != null && !candidates.contains(ids[i])) continue;
                    double norm = (double) tfs[i] / docs.get(ids[i]).tokens.size();
                    scores.merge(ids[i], norm * w, Double::sum);
                }
            }
            List<Scored> all = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : scores.entrySet()) {
                all.add(new Scored(Math.round(e.getValue() * 1e6) / 1e6, e.getKey()));
            }
            List<Scored> best = topK(all, k);                                        // heap
            res.ranked = mergeSort(best, (x, y) -> {                                 // sort
                int c = Double.compare(y.score, x.score);
                return c != 0 ? c : Integer.compare(x.docId, y.docId);
            });
            return res;
        }

        String snippet(Document doc, List<String> terms, int width) {
            String low = doc.text.toLowerCase();
            for (String t : terms) {
                int pos = low.indexOf(t);
                if (pos != -1) {
                    int s = Math.max(0, pos - width / 3);
                    int e = Math.min(doc.text.length(), s + width);
                    return "..." + doc.text.substring(s, e).replace("\n", " ") + "...";
                }
            }
            return doc.text.substring(0, Math.min(width, doc.text.length())) + "...";
        }

        void show(String query, boolean requireAll) {
            history.push(query);
            SearchResult res = search(query, 5, requireAll);
            List<String> terms = tokenize(query);
            for (String[] h : res.hints) System.out.println("  Did you mean '" + h[1] + "' instead of '" + h[0] + "'?");
            if (res.ranked.isEmpty()) { System.out.println("  No results."); return; }
            int rank = 1;
            for (Scored s : res.ranked) {
                Document d = docs.get(s.docId);
                System.out.printf("  %d. [%d] %s  (score %.4f)%n", rank++, d.id, d.name, s.score);
                System.out.println("     " + snippet(d, terms, 90));
            }
        }
    }

    // ------------------------------------------------------------------
    // SAMPLE DATA (so the project runs with 50 documents out of the box)
    // ------------------------------------------------------------------
    static final Map<String, String[]> TOPICS = new LinkedHashMap<>();
    static {
        TOPICS.put("space", new String[]{
                "The rocket launched from the pad and reached orbit in nine minutes.",
                "Astronauts aboard the station conduct experiments in microgravity.",
                "A telescope captured a distant galaxy with remarkable detail.",
                "The planet Mars has a thin atmosphere and polar ice caps.",
                "Satellites relay communication signals around the earth."});
        TOPICS.put("cooking", new String[]{
                "Simmer the garlic and onion in olive oil until golden.",
                "Fresh basil and tomato make a simple pasta sauce.",
                "Bake the bread at high heat for a crisp crust.",
                "Season the chicken with salt, pepper and lemon before roasting.",
                "A good soup needs patience and slow cooking."});
        TOPICS.put("programming", new String[]{
                "A hash table gives fast lookup using a hash function.",
                "The compiler converts source code into machine code.",
                "Recursion solves a problem by calling the function on smaller input.",
                "A binary search tree keeps its keys in sorted order.",
                "Testing and debugging are essential parts of software development."});
        TOPICS.put("health", new String[]{
                "Regular exercise improves heart health and mood.",
                "Drinking water and sleeping well support a strong immune system.",
                "A balanced diet includes vegetables, fruit and protein.",
                "Stretching reduces injury risk during running.",
                "Meditation can lower stress and improve focus."});
        TOPICS.put("history", new String[]{
                "The ancient empire built roads and aqueducts across the land.",
                "The printing press spread knowledge across Europe.",
                "Explorers sailed across the ocean to find new trade routes.",
                "The industrial revolution moved workers from farms to factories.",
                "Museums preserve artifacts and tell the story of the past."});
    }

    static void makeSampleDocs(Path folder, int n) throws IOException {
        Files.createDirectories(folder);
        Random rnd = new Random(9);
        List<String> names = new ArrayList<>(TOPICS.keySet());
        for (int i = 0; i < n; i++) {
            String main = names.get(i % names.size());
            String other;
            do { other = names.get(rnd.nextInt(names.size())); } while (other.equals(main));
            List<String> pool = new ArrayList<>(Arrays.asList(TOPICS.get(main)));
            Collections.shuffle(pool, rnd);
            List<String> sents = new ArrayList<>(pool.subList(0, 4));
            sents.add(TOPICS.get(other)[rnd.nextInt(5)]);
            Collections.shuffle(sents, rnd);
            Files.writeString(folder.resolve(String.format("doc_%03d_%s.txt", i, main)), String.join(" ", sents));
        }
    }

    // ------------------------------------------------------------------
    // COMMAND-LINE INTERFACE
    // ------------------------------------------------------------------
    static final String HELP = String.join("\n",
            "Commands:",
            "  <words>           search (any word matches, ranked by TF-IDF)",
            "  all: <words>      search where ALL words must appear",
            "  /complete <pre>   autocomplete a word prefix",
            "  /related <id>     documents related to <id> (BFS, 2 hops)",
            "  /open <id>        show a full document",
            "  /history          show recent searches (newest first)",
            "  /back             re-run the previous search (pops the stack)",
            "  /help  /quit");

    /** Returns false when the user wants to quit. */
    static boolean handle(Engine engine, String line) {
        line = line.trim();
        if (line.isEmpty()) return true;
        if (line.equals("/quit")) return false;
        try {
            if (line.equals("/help")) {
                System.out.println(HELP);
            } else if (line.startsWith("/complete ")) {
                List<String> r = engine.trie.autocomplete(line.substring(10).trim().toLowerCase(), 8);
                System.out.println("  " + (r.isEmpty() ? "no matches" : r));
            } else if (line.startsWith("/related ")) {
                for (int[] r : engine.related(Integer.parseInt(line.substring(9).trim()), 2)) {
                    System.out.printf("  [%d] %s  (%d hop%s)%n", r[0], engine.docs.get(r[0]).name, r[1], r[1] > 1 ? "s" : "");
                }
            } else if (line.startsWith("/open ")) {
                Document d = engine.docs.get(Integer.parseInt(line.substring(6).trim()));
                System.out.println("  " + d.name + "\n  " + d.text);
            } else if (line.equals("/history")) {
                int shown = 0;
                for (String q : engine.history.newestFirst()) { if (shown++ == 10) break; System.out.println("  - " + q.trim()); }
            } else if (line.equals("/back")) {
                engine.history.pop();                       // discard the current search
                String prev = engine.history.pop();
                if (prev == null) System.out.println("  History is empty.");
                else {
                    System.out.println("  Re-running: " + prev.trim());
                    engine.show(prev, false);
                }
            } else if (line.startsWith("all:")) {
                engine.show(line.substring(4), true);
            } else {
                engine.show(line, false);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            System.out.println("  Invalid document id.");
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(DOCS_DIR);
        boolean empty = true;
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) { empty = s.noneMatch(p -> p.toString().endsWith(".txt")); }
        }
        if (empty) {
            System.out.println("Creating 50 sample documents in ./" + DOCS_DIR + "/ ...");
            makeSampleDocs(dir, 50);
        }
        Engine engine = new Engine();
        engine.loadFolder(dir);
        System.out.println("Indexed " + engine.docs.size() + " documents, " + engine.index.count + " unique terms.");

        if (Arrays.asList(args).contains("--demo")) {
            for (String cmd : new String[]{"rocket orbit", "all: garlic olive", "tomatoo sauce",
                    "/complete pro", "/related 0", "/history", "/back"}) {
                System.out.println("\n> " + cmd);
                handle(engine, cmd);
            }
            return;
        }
        System.out.println(HELP);
        try (Scanner in = new Scanner(System.in)) {     // auto-closed when the loop ends
            while (true) {
                System.out.print("\n> ");
                if (!in.hasNextLine() || !handle(engine, in.nextLine())) break;
            }
        }
        System.out.println("Goodbye!");
    }
}