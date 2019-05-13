module.exports = function(io) {

	var mSockets = [];
	var connection_state = [];

	io.on('connection', function(socket) {
		console.log('[Server]', socket.id + ' joined');
		mSockets.push(socket);
		socket.emit('id', socket.id);
	
		socket.on('init', function(room) {
			console.log('[Server]', socket.id + ' init');
			socket.broadcast.to(room).emit('message', {
					type: "init",
					from: socket.id
			});
		});		
	
		socket.on('message', function(room, message) {
			console.log('[Client ' + socket.id + ']', message);
			socket.broadcast.to(room).emit('message', message);
		});

		socket.on('create or join', function(room) {
			console.log('[Server]', 'Received request from Client ' + socket.id + ' to create or join room ' + room);
			
			var room_object = io.sockets.adapter.rooms[room];
			var numClients;
			
			if (room_object) {
				numClients = Object.keys(room_object).length;
			}   
			
			if (!room_object || numClients == 0) {
				socket.join(room);
				connection_state.push({id: socket.id, room: room});
				console.log('[Server]', 'Client ' + socket.id + ' created room ' + room);
				socket.emit('created', room, socket.id);
				room_object = io.sockets.adapter.rooms[room];
				numClients = Object.keys(room_object).length;
				console.log('[Server]', 'Room ' + room + ' now has ' + numClients + ' client(s)');
			} else if (numClients == 1) {
				console.log('[Server]', 'Client ' + socket.id + ' joined room ' + room);
				socket.broadcast.to(room).emit('join', room);
				socket.join(room);
				connection_state.push({id: socket.id, room: room});
				socket.emit('joined', room, socket.id);
				numClients = Object.keys(room_object).length;
				console.log('[Server]', 'Room ' + room + ' now has ' + numClients + ' client(s)');
				socket.emit('init', room);
			} else { // max 2 clients
				socket.emit('full', room);
				console.log('[Server]', 'Room ' + room + ' is full.');
			}
		});
	
		function leave() {
			var index = 0;
			while (index < mSockets.length && mSockets[index].id != socket.id) {
				index++;
			}
			mSockets.splice(index, 1);
			
			var connection_state_index = connection_state.map(function(t) {
				return t.id;
			}).indexOf(socket.id);
			var room_to_leave = connection_state[connection_state_index].room;
			socket.broadcast.to(room_to_leave).emit('remotepeer leave', room_to_leave);
			
			connection_state.splice(connection_state_index, 1);
			
			console.log('[Server]', 'Client ' + socket.id + ' left room ' + room_to_leave);
		}
	
		socket.on('disconnect', leave);
		
		socket.on('bye', function(room) {
			socket.broadcast.to(room).emit('remotepeer leave', room);
		});
	});
};