# Featured Badges - Quick Start Guide

## For End Users

### How to Feature Your Badges

1. **Navigate to Badges Tab**
   - Click on the "Badges" menu item in the main navigation

2. **Find the Featured Badges Section**
   - Look for the section titled "Featured Badges (Displayed on Public Profile)"
   - You'll see up to 2 badge slots

3. **Select a Badge to Feature**
   - Click the empty slot (➕ icon)
   - A modal will appear showing all your earned badges
   - Click any badge you want to feature
   - The badge will immediately appear in your Featured section

4. **Feature a Second Badge (Optional)**
   - If you have room, click the second empty slot
   - Select another earned badge from the modal
   - Both badges are now featured

5. **View Your Public Profile**
   - Click "View Public Profile" button
   - Scroll to the "⭐ Featured Achievements" section
   - Your featured badges are displayed with a cyan glow effect

### Tips & Tricks

- **Maximum 2 Featured Badges**: You can feature up to 2 badges on your public profile
- **Only Unlocked Badges**: You can only feature badges you've already earned
- **Quick Toggle**: Click a featured badge again to remove it from the featured section
- **Instant Updates**: Changes appear immediately without needing to reload
- **Filter by Tab**: Use the "Earned Badges" tab to see only your unlocked badges

---

## For Developers

### Testing the Feature

**Prerequisites:**

- Backend running on `http://localhost:8080`
- Frontend running on `http://localhost:5173`
- User logged in with some earned badges

**Test Scenario 1: Basic Feature**

```
1. Open BadgeCenter component
2. Verify heading shows "Featured Badges (Displayed on Public Profile)"
3. Click empty slot (➕)
4. Modal appears with earned badges
5. Click a badge to feature
6. Badge appears in Featured section (optimistic update)
7. Check browser console for success message
```

**Test Scenario 2: Max Limit**

```
1. Feature two badges
2. Try to click the empty slot to feature a third
3. Verify alert shows "You can feature a maximum of 2 badges"
4. Backend should return 400 error
```

**Test Scenario 3: Public Profile Display**

```
1. Feature two badges
2. Click "View Public Profile"
3. Verify "⭐ Featured Achievements" section shows both badges
4. Hover over badges to verify hover effects
5. Check responsive layout on mobile (grid cols-2)
```

**Test Scenario 4: Offline & Reload**

```
1. Feature a badge
2. Hard refresh the page (Ctrl+F5)
3. Verify featured badge is still there (data persisted)
4. Featured badge count should match backend data
```

### API Testing with curl/Postman

**Feature a Badge:**

```bash
curl -X PUT http://localhost:8080/api/users/{userId}/profile/featured-badges \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"badgeId": "founding-dev"}'
```

**Expected Response (200 OK):**

```json
{
  "id": "user-id",
  "fullName": "User Name",
  "badges": ["founding-dev", "campus-catalyst", ...],
  "featuredBadges": ["founding-dev"],
  "displayedBadges": [...],
  ...
}
```

**Error Cases:**

- **400 Badge not earned**: User tries to feature unearned badge
- **400 Max limit exceeded**: User tries to feature 3rd badge
- **400 Missing badgeId**: Request body missing badgeId field
- **404 User not found**: Invalid userId in URL
- **500 Server error**: Unexpected error (check logs)

### Frontend State Management

**Key States in BadgeCenter:**

```javascript
const [showFeaturedBadgeModal, setShowFeaturedBadgeModal] = useState(false);
const [featuredBadgesLoading, setFeaturedBadgesLoading] = useState(false);
```

**User Object (from API):**

```javascript
user = {
  _id: "user-id",
  badges: ["founding-dev", "campus-catalyst"], // All earned
  featuredBadges: ["founding-dev"],             // Featured (max 2)
  displayedBadges: ["founding-dev", ...],       // Legacy field
  ...
}
```

### Backend Structure

**Model Field:**

```java
private List<String> featuredBadges = new ArrayList<>();
```

**Endpoint:**

- Path: `/api/users/{userId}/profile/featured-badges`
- Method: PUT
- Request: `{ "badgeId": "string" }`
- Response: Complete User object
- Validations:
  - Badge must exist in user.badges
  - Max 2 featured badges
  - BadgeId required in request

### Database Schema (MongoDB)

**User Document:**

```json
{
  "_id": "ObjectId",
  "fullName": "User Name",
  "badges": ["founding-dev", "campus-catalyst"],
  "featuredBadges": ["founding-dev"],
  "displayedBadges": ["founding-dev", "campus-catalyst"],
  ...
}
```

---

## Troubleshooting

### Issue: Empty slot doesn't appear clickable

**Solution:** Make sure `getActiveBadges().length < 2`. Featured badges count towards this limit.

### Issue: Modal doesn't show earned badges

**Solution:** Verify `earnedBadges` array is populated. Check browser console for data.

### Issue: Featured badge not persisting after reload

**Solution:** Check backend response in Network tab. Ensure `setUser()` updates parent state correctly.

### Issue: "You can feature a maximum of 2 badges" error when clicking empty slot

**Solution:** This is expected behavior. You can only have 2 featured badges total.

### Issue: Badge appears in modal even though it's already featured

**Solution:** Modal filters with `.filter(badge => !badge.isActive)`. Ensure `badge.isActive` is set correctly after selection.

---

## Performance Considerations

1. **Modal Data**: Loads earned badges from already-loaded user object (no extra API call)
2. **Optimistic Updates**: UI updates before server response for instant feedback
3. **Lazy Loading**: Featured badges modal only renders when opened
4. **Caching**: Featured badges are cached in user state; no repeated API calls

---

## Future Enhancement Ideas

1. **Drag & Drop**: Reorder featured badges
2. **Badge Details**: Show badge description/perks on hover in public profile
3. **Animations**: Confetti or pulse effect when badge is featured
4. **Share**: Social share button for featured badges
5. **Badges Feed**: Featured badges appear in user cards across the platform
6. **Statistics**: Track most featured badge statistics
