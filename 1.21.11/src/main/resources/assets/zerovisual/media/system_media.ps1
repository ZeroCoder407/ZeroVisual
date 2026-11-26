# ZeroVisual: what plays in Windows (the media overlay next to the volume: Yandex Music, VK in a browser, Spotify, ...)
# and its buttons. Started by the game (SystemMedia.java); one JSON object per line on stdout, commands on stdin:
# play, pause, toggle, next, previous, seek <ms>, quit. Only Windows' own media API, nothing is sent anywhere.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
[Console]::InputEncoding = [Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($operation, [Type]$type) {
	$task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
	$null = $task.Wait(3000)
	return $task.Result
}

function Out($object) {
	[Console]::Out.WriteLine(($object | ConvertTo-Json -Compress))
	[Console]::Out.Flush()
}

$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
# Console.In reads synchronously even through ReadLineAsync: a StreamReader on the raw stream does not block the loop
$stdin = New-Object IO.StreamReader([Console]::OpenStandardInput(), [Text.Encoding]::UTF8)
$pending = $stdin.ReadLineAsync()
$lastKey = ''
$lastSent = 0

while ($true) {
	# commands from the game
	while ($pending.IsCompleted) {
		$line = $pending.Result
		if ($null -eq $line -or $line -eq 'quit') { exit 0 }
		$pending = $stdin.ReadLineAsync()
		$session = $manager.GetCurrentSession()
		if ($null -eq $session) { continue }
		try {
			$parts = $line.Split(' ')
			switch ($parts[0]) {
				'play' { $null = Await ($session.TryPlayAsync()) ([bool]) }
				'pause' { $null = Await ($session.TryPauseAsync()) ([bool]) }
				'toggle' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
				'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
				'previous' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
				'seek' { $null = Await ($session.TryChangePlaybackPositionAsync([long]$parts[1] * 10000)) ([bool]) }
			}
		} catch { }
		$lastSent = 0
	}

	try {
		$session = $manager.GetCurrentSession()
		if ($null -eq $session) {
			if ($lastKey -ne 'none') { Out @{ t = 'none' }; $lastKey = 'none' }
		} else {
			$props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			$info = $session.GetPlaybackInfo()
			$time = $session.GetTimelineProperties()
			$key = "$($session.SourceAppUserModelId)|$($props.Artist)|$($props.Title)"
			$now = [Environment]::TickCount
			$status = "$($info.PlaybackStatus)".ToLowerInvariant()
			# the position as of now: apps report it now and then, with the time of the report
			$position = $time.Position.TotalMilliseconds
			if ($status -eq 'playing' -and $time.LastUpdatedTime.Year -gt 2000) {
				$position += ([DateTimeOffset]::Now - $time.LastUpdatedTime).TotalMilliseconds
			}
			if ($key -ne $lastKey -or $now - $lastSent -gt 1000) {
				Out @{
					t = 'media'
					app = "$($session.SourceAppUserModelId)"
					title = "$($props.Title)"
					artist = "$($props.Artist)"
					album = "$($props.AlbumTitle)"
					status = $status
					pos = [long][Math]::Max(0, $position)
					dur = [long]$time.EndTime.TotalMilliseconds
					seek = [bool]$info.Controls.IsPlaybackPositionEnabled
					next = [bool]$info.Controls.IsNextEnabled
					previous = [bool]$info.Controls.IsPreviousEnabled
				}
				$lastSent = $now
			}
			if ($key -ne $lastKey) {
				$lastKey = $key
				# the cover, once per track
				$cover = ''
				if ($null -ne $props.Thumbnail) {
					try {
						$stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
						if ($stream.Size -gt 0 -and $stream.Size -lt 2000000) {
							$reader = [Windows.Storage.Streams.DataReader]::new($stream)
							$null = Await ($reader.LoadAsync([uint32]$stream.Size)) ([uint32])
							$bytes = New-Object byte[] $stream.Size
							$reader.ReadBytes($bytes)
							$cover = [Convert]::ToBase64String($bytes)
						}
					} catch { }
				}
				Out @{ t = 'cover'; key = $key; data = $cover }
			}
		}
	} catch {
		if ($lastKey -ne 'none') { Out @{ t = 'none' }; $lastKey = 'none' }
	}
	Start-Sleep -Milliseconds 250
}
