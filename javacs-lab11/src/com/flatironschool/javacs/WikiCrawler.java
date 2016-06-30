package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;


import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	final static String WIKI_BASE_URL = "https://en.wikipedia.org";

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        	String url = queue.poll();
        	if (url == null) return null; 

        	Elements paragraphs = wf.readWikipedia(url); 
        	// Testing logic
        	if (testing){
        		index.indexPage(url, paragraphs); 
        		queueInternalLinks(paragraphs);
        	}
        	// Non-Testing Logic
        	else{
        		if (index.isIndexed(url)){
        			return null; 
        		}
        		queueInternalLinks(paragraphs); 
        	}
		return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        	for (Element paragraph: paragraphs){
                        Iterable<Node> iter = new WikiNodeIterable(paragraph);
                        int parenthesis_count = 0; 
                        // put into own function
                        for (Node node: iter){
                                if (node instanceof Element){
                                        String link = node.attr("href");
                                        String thresholdText = "/wiki/";
                                        Element elem = (Element)node; 
                                        if (isValidLink(elem) && parenthesis_count == 0){
                                                enqueue(link);
                                        }
                                }
                        }
                }
	}
	private void enqueue(String link){
		String url = WIKI_BASE_URL + link;
	        queue.add(url);
	}

	private static Boolean isValidLink(Element elem){
                String link = elem.attr("href");
                return isLinkWikiPage(link);       
        }

        private static Boolean isLinkWikiPage(String link){
                String thresholdText = "/wiki/";
                return link.startsWith(thresholdText); 
        }

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
