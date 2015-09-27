package collections;

public class TestConcurrentHashMap {

	public static void main(String[] args) {
		ConcurrentHashMap map = new ConcurrentHashMap();
		map.put("c", 4);
		map.put("321", 5);
		map.put("v", 6);
		System.out.println("==========remove==================");
		map.remove("321");
	}

}
