import java.util.*;
import java.time.*;
import java.util.stream.*;

/**
 * ArtStation-style Feed Algorithm
 * Combines personalized recommendations, trending content, and quality scoring
 */

// Core Models
class Artwork {
    String id;
    String title;
    String artistId;
    Set<String> tags;
    LocalDateTime uploadDate;
    int likes;
    int views;
    int comments;
    double qualityScore; // 0-100
    String category; // "illustration", "3d", "concept-art", etc.
    
    public Artwork(String id, String title, String artistId, Set<String> tags, 
                   LocalDateTime uploadDate, int likes, int views, int comments, 
                   double qualityScore, String category) {
        this.id = id;
        this.title = title;
        this.artistId = artistId;
        this.tags = tags;
        this.uploadDate = uploadDate;
        this.likes = likes;
        this.views = views;
        this.comments = comments;
        this.qualityScore = qualityScore;
        this.category = category;
    }
    
    public double getEngagementRate() {
        return views > 0 ? (double)(likes + comments * 2) / views : 0;
    }
}

class User {
    String id;
    Set<String> followedArtists;
    Set<String> likedArtworks;
    Set<String> preferredTags;
    Set<String> preferredCategories;
    Map<String, Integer> tagInteractions; // tag -> interaction count
    
    public User(String id) {
        this.id = id;
        this.followedArtists = new HashSet<>();
        this.likedArtworks = new HashSet<>();
        this.preferredTags = new HashSet<>();
        this.preferredCategories = new HashSet<>();
        this.tagInteractions = new HashMap<>();
    }
}

// Main Feed Algorithm
class ArtStationFeedAlgorithm {
    private static final double RECENCY_WEIGHT = 0.25;
    private static final double ENGAGEMENT_WEIGHT = 0.20;
    private static final double QUALITY_WEIGHT = 0.15;
    private static final double PERSONALIZATION_WEIGHT = 0.25;
    private static final double TRENDING_WEIGHT = 0.15;
    
    private static final int TRENDING_WINDOW_HOURS = 48;
    private static final int FEED_SIZE = 50;
    
    /**
     * Generate personalized feed for a user
     */
    public List<Artwork> generateFeed(User user, List<Artwork> allArtworks) {
        LocalDateTime now = LocalDateTime.now();
        
        // Calculate scores for each artwork
        List<ScoredArtwork> scoredArtworks = allArtworks.stream()
            .map(artwork -> {
                double score = calculateFeedScore(user, artwork, now);
                return new ScoredArtwork(artwork, score);
            })
            .sorted(Comparator.comparingDouble(ScoredArtwork::getScore).reversed())
            .collect(Collectors.toList());
        
        // Apply diversity filter to avoid showing too similar content
        List<Artwork> diversifiedFeed = applyDiversityFilter(scoredArtworks, FEED_SIZE);
        
        return diversifiedFeed;
    }
    
    /**
     * Calculate composite score for an artwork in the feed
     */
    private double calculateFeedScore(User user, Artwork artwork, LocalDateTime now) {
        double recencyScore = calculateRecencyScore(artwork, now);
        double engagementScore = calculateEngagementScore(artwork);
        double qualityScore = artwork.qualityScore / 100.0;
        double personalizationScore = calculatePersonalizationScore(user, artwork);
        double trendingScore = calculateTrendingScore(artwork, now);
        
        return (recencyScore * RECENCY_WEIGHT) +
               (engagementScore * ENGAGEMENT_WEIGHT) +
               (qualityScore * QUALITY_WEIGHT) +
               (personalizationScore * PERSONALIZATION_WEIGHT) +
               (trendingScore * TRENDING_WEIGHT);
    }
    
    /**
     * Recency score - exponential decay over time
     */
    private double calculateRecencyScore(Artwork artwork, LocalDateTime now) {
        long hoursOld = Duration.between(artwork.uploadDate, now).toHours();
        double decayRate = 0.05; // Adjust for faster/slower decay
        return Math.exp(-decayRate * hoursOld / 24.0);
    }
    
    /**
     * Engagement score based on likes, views, and comments
     */
    private double calculateEngagementScore(Artwork artwork) {
        double engagementRate = artwork.getEngagementRate();
        
        // Normalize using sigmoid function
        return 1.0 / (1.0 + Math.exp(-5 * (engagementRate - 0.1)));
    }
    
    /**
     * Personalization score based on user preferences
     */
    private double calculatePersonalizationScore(User user, Artwork artwork) {
        double score = 0.0;
        
        // Boost if from followed artist
        if (user.followedArtists.contains(artwork.artistId)) {
            score += 0.4;
        }
        
        // Tag matching
        int matchingTags = 0;
        int totalUserTags = user.preferredTags.size();
        for (String tag : artwork.tags) {
            if (user.preferredTags.contains(tag)) {
                matchingTags++;
                // Weight by interaction frequency
                score += 0.05 * user.tagInteractions.getOrDefault(tag, 1);
            }
        }
        
        if (totalUserTags > 0) {
            score += 0.3 * ((double) matchingTags / totalUserTags);
        }
        
        // Category preference
        if (user.preferredCategories.contains(artwork.category)) {
            score += 0.2;
        }
        
        // Penalize if already liked
        if (user.likedArtworks.contains(artwork.id)) {
            score *= 0.1;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Trending score - identifies rapidly growing engagement
     */
    private double calculateTrendingScore(Artwork artwork, LocalDateTime now) {
        long hoursOld = Duration.between(artwork.uploadDate, now).toHours();
        
        if (hoursOld > TRENDING_WINDOW_HOURS) {
            return 0.0;
        }
        
        // Calculate velocity of engagement
        double engagementVelocity = artwork.likes + (artwork.comments * 2);
        if (hoursOld > 0) {
            engagementVelocity /= hoursOld;
        }
        
        // Normalize
        return Math.min(engagementVelocity / 100.0, 1.0);
    }
    
    /**
     * Apply diversity filter to avoid monotonous feed
     */
    private List<Artwork> applyDiversityFilter(List<ScoredArtwork> scoredArtworks, int targetSize) {
        List<Artwork> diverseFeed = new ArrayList<>();
        Set<String> usedArtists = new HashSet<>();
        Set<String> usedCategories = new HashSet<>();
        
        int sameArtistLimit = 2;
        int sameCategoryLimit = 5;
        
        for (ScoredArtwork scored : scoredArtworks) {
            if (diverseFeed.size() >= targetSize) {
                break;
            }
            
            Artwork artwork = scored.artwork;
            
            // Count occurrences
            long artistCount = diverseFeed.stream()
                .filter(a -> a.artistId.equals(artwork.artistId))
                .count();
            
            long categoryCount = diverseFeed.stream()
                .filter(a -> a.category.equals(artwork.category))
                .count();
            
            // Apply limits
            if (artistCount < sameArtistLimit && categoryCount < sameCategoryLimit) {
                diverseFeed.add(artwork);
                usedArtists.add(artwork.artistId);
                usedCategories.add(artwork.category);
            }
        }
        
        return diverseFeed;
    }
    
    /**
     * Helper class to store artwork with its score
     */
    private static class ScoredArtwork {
        Artwork artwork;
        double score;
        
        ScoredArtwork(Artwork artwork, double score) {
            this.artwork = artwork;
            this.score = score;
        }
        
        double getScore() {
            return score;
        }
    }
}

// Additional utility for content discovery
class ContentDiscovery {
    /**
     * Find similar artworks based on tags and category
     */
    public List<Artwork> findSimilar(Artwork artwork, List<Artwork> allArtworks, int limit) {
        return allArtworks.stream()
            .filter(a -> !a.id.equals(artwork.id))
            .map(a -> {
                double similarity = calculateSimilarity(artwork, a);
                return new AbstractMap.SimpleEntry<>(a, similarity);
            })
            .sorted(Map.Entry.<Artwork, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private double calculateSimilarity(Artwork a1, Artwork a2) {
        double score = 0.0;
        
        // Category match
        if (a1.category.equals(a2.category)) {
            score += 0.3;
        }
        
        // Tag overlap (Jaccard similarity)
        Set<String> intersection = new HashSet<>(a1.tags);
        intersection.retainAll(a2.tags);
        
        Set<String> union = new HashSet<>(a1.tags);
        union.addAll(a2.tags);
        
        if (!union.isEmpty()) {
            score += 0.7 * ((double) intersection.size() / union.size());
        }
        
        return score;
    }
}

// Example usage
class FeedDemo {
    public static void main(String[] args) {
        // Create sample user
        User user = new User("user1");
        user.followedArtists.add("artist1");
        user.preferredTags.addAll(Arrays.asList("fantasy", "concept-art", "character-design"));
        user.preferredCategories.add("illustration");
        user.tagInteractions.put("fantasy", 15);
        user.tagInteractions.put("concept-art", 10);
        
        // Create sample artworks
        List<Artwork> artworks = new ArrayList<>();
        artworks.add(new Artwork("art1", "Dragon Knight", "artist1",
            new HashSet<>(Arrays.asList("fantasy", "character-design")),
            LocalDateTime.now().minusHours(5), 250, 1500, 30, 85.0, "illustration"));
        
        artworks.add(new Artwork("art2", "Sci-Fi City", "artist2",
            new HashSet<>(Arrays.asList("sci-fi", "concept-art", "environment")),
            LocalDateTime.now().minusHours(2), 500, 3000, 60, 92.0, "concept-art"));
        
        artworks.add(new Artwork("art3", "Character Study", "artist1",
            new HashSet<>(Arrays.asList("character-design", "portrait")),
            LocalDateTime.now().minusHours(12), 180, 1200, 25, 78.0, "illustration"));
        
        // Generate feed
        ArtStationFeedAlgorithm algorithm = new ArtStationFeedAlgorithm();
        List<Artwork> feed = algorithm.generateFeed(user, artworks);
        
        // Display feed
        System.out.println("Personalized Feed:");
        for (int i = 0; i < feed.size(); i++) {
            Artwork art = feed.get(i);
            System.out.println((i + 1) + ". " + art.title + " by " + art.artistId + 
                             " (" + art.category + ") - " + art.likes + " likes");
        }
    }
}