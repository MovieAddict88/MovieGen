# SQLite Export Functionality for Api Manager

## Overview

The Api Manager Android app has been enhanced to support exporting data to SQLite database format (`playlist.db`) in addition to the existing JSON format (`playlist.json`). This allows for better compatibility with movie apps that use SQLite databases as their data source.

## New Features

### 1. SQLite Database Export
- Export all content items to a SQLite database file (`playlist.db`)
- Maintains the same data structure as the reference `playlist.db` from the MovieAddict88 repository
- Supports all content types: Live TV, Movies, TV Series
- Preserves DRM information and server configurations

### 2. Enhanced Export Options
- **Export as JSON**: Traditional JSON format export
- **Export as SQLite Database**: New SQLite database export
- **Export Both**: Export in both formats simultaneously

### 3. Enhanced GitHub Upload
- **Upload as JSON**: Upload JSON file to GitHub
- **Upload as SQLite Database**: Upload SQLite database file to GitHub
- **Upload Both**: Upload both formats to GitHub

## Database Structure

The exported SQLite database follows the same structure as the reference `playlist.db`:

### Tables

#### `entries` table
- `id` (INTEGER PRIMARY KEY)
- `title` (TEXT)
- `sub_category` (TEXT)
- `country` (TEXT)
- `description` (TEXT)
- `poster` (TEXT)
- `thumbnail` (TEXT)
- `rating` (TEXT)
- `duration` (TEXT)
- `year` (TEXT)
- `main_category` (TEXT)
- `servers_json` (TEXT) - JSON array of server objects
- `seasons_json` (TEXT) - JSON object for TV series seasons/episodes
- `related_json` (TEXT) - Reserved for future use

#### `categories` table
- `id` (INTEGER PRIMARY KEY)
- `main_category` (TEXT)
- `sub_categories` (TEXT) - JSON array of sub-categories

#### `metadata` table
- `id` (INTEGER PRIMARY KEY)
- `last_updated` (TEXT)
- `source_url` (TEXT)
- `total_entries` (INTEGER)
- `version` (TEXT)

## How to Use

### 1. Export to Local Storage

1. Open the Api Manager app
2. Navigate to the **Data Management** tab
3. Tap **Export Data**
4. Choose your preferred export format:
   - **Export as JSON**: Creates `cinecraze_playlist_[timestamp].json`
   - **Export as SQLite Database**: Creates `playlist.db`
   - **Export Both**: Creates both files

### 2. Upload to GitHub

1. In the **Data Management** tab, configure your GitHub settings:
   - **GitHub Token**: Your personal access token
   - **Repository**: Target repository (e.g., `MovieAddict88/Movie-Source`)
   - **File Path**: Target file path (default: `playlist.db`)

2. Tap **Upload to GitHub**
3. Choose your preferred upload format:
   - **Upload as JSON**: Uploads JSON file
   - **Upload as SQLite Database**: Uploads SQLite database file
   - **Upload Both**: Uploads both formats

### 3. Testing the SQLite Export

1. In the **Data Management** tab, tap **Test SQLite Export** (if available)
2. This will create a test database with sample data
3. Check the logs for detailed information about the export process

## File Locations

### Exported Files
- **JSON files**: `Downloads/cinecraze_playlist_[timestamp].json`
- **SQLite database**: `Downloads/playlist.db`

### GitHub Upload
- **JSON**: `https://github.com/[owner]/[repo]/blob/main/playlist.json`
- **SQLite**: `https://github.com/[owner]/[repo]/blob/main/playlist.db`

## Data Mapping

### Content Types
- **Live TV**: `main_category = "Live TV"`
- **Movies**: `main_category = "Movie"`
- **TV Series**: `main_category = "TV Series"`

### Server Format
Servers are stored as JSON arrays in the `servers_json` field:

```json
[
  {
    "name": "HD",
    "url": "https://example.com/stream.m3u8",
    "drm": false
  },
  {
    "name": "4K",
    "url": "https://example.com/stream.mpd",
    "drm": true,
    "license": "your-license-key"
  }
]
```

### TV Series Structure
TV series episodes are stored in the `seasons_json` field:

```json
{
  "season": 1,
  "episode": 1,
  "title": "Episode 1",
  "description": "Episode description",
  "thumbnail": "https://example.com/episode1.jpg",
  "duration": "45:00",
  "servers": [...]
}
```

## Compatibility

The exported SQLite database is compatible with:
- Movie apps that use the same database structure
- The reference `playlist.db` from MovieAddict88/Movie-Source
- Standard SQLite database tools and viewers

## Technical Details

### Dependencies
- Android SQLite (built-in)
- Gson for JSON serialization
- Room database annotations (for model classes)

### File Size
- SQLite databases are typically smaller than equivalent JSON files
- Better compression and indexing
- Faster query performance for movie apps

### Error Handling
- Comprehensive error logging
- Graceful fallback to JSON export if SQLite export fails
- User-friendly error messages

## Troubleshooting

### Common Issues

1. **Export fails**: Check device storage space
2. **Upload fails**: Verify GitHub token and repository permissions
3. **Database corruption**: Re-export the data
4. **Missing data**: Ensure all content items have required fields

### Logs
Check Android logs for detailed error information:
```bash
adb logcat | grep -E "(SQLiteExporter|DataManager|GitHubService)"
```

## Future Enhancements

- Import from SQLite databases
- Database schema versioning
- Incremental updates
- Database optimization tools
- Backup and restore functionality

## Support

For issues or questions about the SQLite export functionality:
1. Check the logs for error details
2. Verify your data structure
3. Test with sample data first
4. Report issues with detailed error messages